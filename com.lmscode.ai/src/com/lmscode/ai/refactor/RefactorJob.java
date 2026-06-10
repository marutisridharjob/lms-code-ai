package com.lmscode.ai.refactor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.compare.CompareUI;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;

import com.lmscode.ai.Activator;
import com.lmscode.ai.client.AiClient;
import com.lmscode.ai.client.AiClientFactory;
import com.lmscode.ai.client.ChatMessage;
import com.lmscode.ai.core.CodeExtractor;
import com.lmscode.ai.core.Prompts;

/**
 * Sends each selected file to the configured AI and either opens a compare
 * editor with the proposal (preview mode) or writes the result directly
 * (apply mode).
 */
public class RefactorJob extends Job {

	private final List<IFile> files;
	private final boolean applyDirectly;

	public RefactorJob(List<IFile> files, boolean applyDirectly) {
		super("LMS Code: refactoring " + files.size() + " file(s)");
		this.files = files;
		this.applyDirectly = applyDirectly;
		setUser(true);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		SubMonitor progress = SubMonitor.convert(monitor, files.size());
		AiClient client = AiClientFactory.fromPreferences();
		MultiStatus result = new MultiStatus(Activator.PLUGIN_ID, 0,
				"LMS Code refactoring finished with problems. See details.", null);

		for (IFile file : files) {
			if (progress.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			progress.subTask(file.getFullPath().toString());
			try {
				String original = read(file);
				String response = client.chat(Prompts.REFACTOR_SYSTEM,
						List.of(ChatMessage.user(Prompts.refactorUser(file.getFullPath().toString(), original))));
				String refactored = CodeExtractor.extractCode(response);
				if (refactored.isBlank()) {
					result.add(Status.warning("Empty AI response for " + file.getFullPath()));
				} else if (refactored.strip().equals(original.strip())) {
					result.add(Status.info("No changes proposed for " + file.getFullPath()));
				} else if (applyDirectly) {
					applyDirectly(file, refactored);
				} else {
					openCompareEditor(file, refactored);
				}
			} catch (Exception e) {
				Activator.logError("Refactoring failed for " + file.getFullPath(), e);
				result.add(Status.error("Failed for " + file.getFullPath() + ": " + e.getMessage(), e));
			}
			progress.worked(1);
		}
		if (result.getChildren().length == 0) {
			return Status.OK_STATUS;
		}
		return result;
	}

	private static String read(IFile file) throws CoreException, IOException {
		Charset charset;
		try {
			charset = Charset.forName(file.getCharset());
		} catch (Exception e) {
			charset = StandardCharsets.UTF_8;
		}
		try (InputStream in = file.getContents(true)) {
			return new String(in.readAllBytes(), charset);
		}
	}

	private static void applyDirectly(IFile file, String content) throws CoreException {
		Charset charset;
		try {
			charset = Charset.forName(file.getCharset());
		} catch (Exception e) {
			charset = StandardCharsets.UTF_8;
		}
		file.setContents(new ByteArrayInputStream(content.getBytes(charset)), true, true, null);
	}

	private static void openCompareEditor(IFile file, String proposedContent) {
		Display.getDefault().asyncExec(
				() -> CompareUI.openCompareEditor(new RefactorCompareInput(file, proposedContent)));
	}
}
