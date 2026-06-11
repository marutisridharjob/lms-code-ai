package com.lmscode.ai.refactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.compare.CompareUI;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;

import com.lmscode.ai.Activator;
import com.lmscode.ai.client.AiClient;
import com.lmscode.ai.client.AiClientFactory;
import com.lmscode.ai.client.ChatMessage;
import com.lmscode.ai.core.FileContents;
import com.lmscode.ai.core.FindingsParser;
import com.lmscode.ai.core.FindingsParser.FileChange;
import com.lmscode.ai.core.FixFinding;
import com.lmscode.ai.core.JavaFormatter;
import com.lmscode.ai.core.Prompts;
import com.lmscode.ai.views.ResponseView;

/**
 * Batch refactoring: sends the selected files (whole content, with their
 * workspace paths) to the AI in size-bounded batches. Suggested changes are
 * applied automatically — each changed file is updated, run through the code
 * formatter and saved — and summarized in the LMS Response view. In preview
 * mode a compare editor opens per changed file instead.
 */
public class RefactorJob extends Job {

	/** Soft cap on characters of file content per AI request. */
	private static final int BATCH_CHAR_LIMIT = 60_000;

	private final List<IFile> files;
	private final boolean applyDirectly;
	private final ResponseView view;

	public RefactorJob(List<IFile> files, boolean applyDirectly, ResponseView view) {
		super("LMS Code: refactoring " + files.size() + " file(s)");
		this.files = files;
		this.applyDirectly = applyDirectly;
		this.view = view;
		setUser(true);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		String source = "Refactor (" + files.size() + " file(s))";
		MultiStatus problems = new MultiStatus(Activator.PLUGIN_ID, 0,
				"LMS Code refactoring finished with problems. See details.", null);

		// Read everything up front so batches can be sized by content.
		Map<String, IFile> byPath = new LinkedHashMap<>();
		Map<String, String> contents = new LinkedHashMap<>();
		for (IFile file : files) {
			try {
				String path = file.getFullPath().toString();
				byPath.put(path, file);
				contents.put(path, FileContents.read(file));
			} catch (Exception e) {
				problems.add(Status.error("Cannot read " + file.getFullPath() + ": " + e.getMessage(), e));
			}
		}

		List<List<String>> batches = batchPaths(contents);
		SubMonitor progress = SubMonitor.convert(monitor, batches.size());
		AiClient client = AiClientFactory.fromPreferences();
		List<FixFinding> summary = new ArrayList<>();
		StringBuilder raw = new StringBuilder();

		for (List<String> batch : batches) {
			if (progress.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			progress.subTask(batch.get(0) + (batch.size() > 1 ? " (+" + (batch.size() - 1) + " more)" : "")); //$NON-NLS-1$ //$NON-NLS-2$
			try {
				StringBuilder filesBlock = new StringBuilder();
				for (String path : batch) {
					filesBlock.append("=== FILE: ").append(path).append(" ===\n") //$NON-NLS-1$ //$NON-NLS-2$
							.append(contents.get(path)).append('\n');
				}
				String response = client.chat(Prompts.REFACTOR_BATCH_SYSTEM,
						List.of(ChatMessage.user(Prompts.refactorBatchUser(batch, filesBlock.toString()))));
				if (!raw.isEmpty()) {
					raw.append("\n\n"); //$NON-NLS-1$
				}
				raw.append(response);

				List<FileChange> changes = FindingsParser.parseFileChanges(response);
				if (changes.isEmpty()) {
					for (String path : batch) {
						summary.add(new FixFinding(path, 0, "INFO", "No changes proposed", "", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					}
					continue;
				}
				for (FileChange change : changes) {
					applyChange(change, byPath, contents, summary, problems);
				}
			} catch (Exception e) {
				Activator.logError("Refactoring batch failed", e);
				problems.add(Status.error("Batch starting at " + batch.get(0) + " failed: " + e.getMessage(), e));
			}
			progress.worked(1);
		}

		String rawText = raw.toString();
		Display.getDefault().asyncExec(() -> view.setResults(source, summary, rawText));
		return problems.getChildren().length == 0 ? Status.OK_STATUS : problems;
	}

	private void applyChange(FileChange change, Map<String, IFile> byPath, Map<String, String> originals,
			List<FixFinding> summary, MultiStatus problems) {
		IFile file = byPath.get(change.file());
		if (file == null) {
			// Model may have normalized the path — try the workspace root.
			try {
				IFile candidate = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(change.file()));
				if (candidate.exists()) {
					file = candidate;
				}
			} catch (RuntimeException ignored) {
				// invalid path from the model
			}
		}
		if (file == null) {
			problems.add(Status.warning("AI suggested a change for unknown file " + change.file()));
			return;
		}
		String path = file.getFullPath().toString();
		String original = originals.get(path);
		String updated = change.content().strip() + "\n"; //$NON-NLS-1$
		if (original != null && original.strip().equals(updated.strip())) {
			summary.add(new FixFinding(path, 0, "INFO", "No effective change", "", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			return;
		}
		try {
			if (applyDirectly) {
				String formatted = JavaFormatter.formatIfJava(file.getName(), updated);
				FileContents.write(file, formatted);
				summary.add(new FixFinding(path, 0, "INFO", //$NON-NLS-1$
						"Refactored, formatted and saved", "", "")); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				final IFile target = file;
				final String proposed = updated;
				Display.getDefault().asyncExec(
						() -> CompareUI.openCompareEditor(new RefactorCompareInput(target, proposed)));
				summary.add(new FixFinding(path, 0, "INFO", //$NON-NLS-1$
						"Change opened in compare editor for review", "", "")); //$NON-NLS-1$ //$NON-NLS-2$
			}
		} catch (Exception e) {
			Activator.logError("Cannot apply refactoring to " + path, e);
			problems.add(Status.error("Cannot apply change to " + path + ": " + e.getMessage(), e));
		}
	}

	/** Groups paths into batches whose combined content stays under the char limit. */
	private static List<List<String>> batchPaths(Map<String, String> contents) {
		List<List<String>> batches = new ArrayList<>();
		List<String> current = new ArrayList<>();
		int size = 0;
		for (Map.Entry<String, String> entry : contents.entrySet()) {
			int length = entry.getValue().length();
			if (!current.isEmpty() && size + length > BATCH_CHAR_LIMIT) {
				batches.add(current);
				current = new ArrayList<>();
				size = 0;
			}
			current.add(entry.getKey());
			size += length;
		}
		if (!current.isEmpty()) {
			batches.add(current);
		}
		return batches;
	}
}
