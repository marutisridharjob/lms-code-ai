package com.lmscode.ai.fix;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
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
import com.lmscode.ai.core.FixFinding;
import com.lmscode.ai.core.Prompts;
import com.lmscode.ai.views.ResponseView;

/**
 * Sends problems (from the Problems view) or whole files to the AI and shows
 * the resulting fix findings in the LMS Response view.
 */
public class FixJob extends Job {

	private static final int SNIPPET_CONTEXT_LINES = 15;

	private final List<IMarker> markers;
	private final List<IFile> files;
	private final ResponseView view;

	private FixJob(String name, List<IMarker> markers, List<IFile> files, ResponseView view) {
		super(name);
		this.markers = markers;
		this.files = files;
		this.view = view;
		setUser(true);
	}

	public static FixJob forProblems(List<IMarker> markers, ResponseView view) {
		return new FixJob("LMS Code: fixing " + markers.size() + " problem(s)", markers, List.of(), view);
	}

	public static FixJob forFiles(List<IFile> files, ResponseView view) {
		return new FixJob("LMS Code: analyzing " + files.size() + " file(s) for issues",
				List.of(), files, view);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		String source = markers.isEmpty()
				? "Fix Issues (" + files.size() + " file(s))"
				: "Fix Problems (" + markers.size() + " problem(s))";
		AiClient client = AiClientFactory.fromPreferences();
		List<FixFinding> all = new ArrayList<>();
		StringBuilder raw = new StringBuilder();
		try {
			if (!markers.isEmpty()) {
				String response = client.chat(Prompts.FIX_SYSTEM,
						List.of(ChatMessage.user(Prompts.fixProblemsUser(problemsBlock(markers)))));
				raw.append(response);
				all.addAll(FindingsParser.parseFindings(response));
			} else {
				SubMonitor progress = SubMonitor.convert(monitor, files.size());
				for (IFile file : files) {
					if (progress.isCanceled()) {
						return Status.CANCEL_STATUS;
					}
					progress.subTask(file.getFullPath().toString());
					String content = FileContents.read(file);
					String response = client.chat(Prompts.FIX_SYSTEM,
							List.of(ChatMessage.user(Prompts.fixFileUser(
									file.getFullPath().toString(), content, markersBlock(file)))));
					if (!raw.isEmpty()) {
						raw.append("\n\n"); //$NON-NLS-1$
					}
					raw.append("=== ").append(file.getFullPath()).append(" ===\n").append(response); //$NON-NLS-1$ //$NON-NLS-2$
					List<FixFinding> findings = FindingsParser.parseFindings(response);
					// Make sure each finding points at a file even if the model omitted it.
					for (FixFinding f : findings) {
						all.add(f.file() == null || f.file().isBlank()
								? new FixFinding(file.getFullPath().toString(), f.line(), f.severity(),
										f.title(), f.description(), f.fix())
								: f);
					}
					progress.worked(1);
				}
			}
			String rawText = raw.toString();
			Display.getDefault().asyncExec(() -> view.setResults(source, all, rawText));
			return Status.OK_STATUS;
		} catch (Exception e) {
			Activator.logError("Fix analysis failed", e);
			Display.getDefault().asyncExec(() -> view.setError(source, e.getMessage()));
			return Status.OK_STATUS; // surfaced in the view
		}
	}

	/** Formats the selected problem markers with file, line, message and code context. */
	private static String problemsBlock(List<IMarker> markers) {
		StringBuilder sb = new StringBuilder();
		int i = 1;
		for (IMarker marker : markers) {
			String path = marker.getResource() != null
					? marker.getResource().getFullPath().toString() : "unknown"; //$NON-NLS-1$
			int line = marker.getAttribute(IMarker.LINE_NUMBER, 0);
			String message = marker.getAttribute(IMarker.MESSAGE, ""); //$NON-NLS-1$
			sb.append("--- Problem ").append(i++).append(" ---\n");
			sb.append("File: ").append(path).append('\n');
			sb.append("Line: ").append(line).append('\n');
			sb.append("Message: ").append(message).append('\n');
			if (marker.getResource() instanceof IFile file && line > 0) {
				String snippet = snippet(file, line);
				if (!snippet.isBlank()) {
					sb.append("Code around line ").append(line).append(":\n").append(snippet);
				}
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	private static String snippet(IFile file, int line) {
		try {
			String[] lines = FileContents.read(file).split("\\R", -1); //$NON-NLS-1$
			int from = Math.max(0, line - 1 - SNIPPET_CONTEXT_LINES);
			int to = Math.min(lines.length, line + SNIPPET_CONTEXT_LINES);
			StringBuilder sb = new StringBuilder();
			for (int i = from; i < to; i++) {
				sb.append(i + 1).append(": ").append(lines[i]).append('\n'); //$NON-NLS-1$
			}
			return sb.toString();
		} catch (Exception e) {
			return ""; //$NON-NLS-1$
		}
	}

	/** Current IDE problems on the file, to give the model extra signal. */
	private static String markersBlock(IFile file) {
		StringBuilder sb = new StringBuilder();
		try {
			for (IMarker marker : file.findMarkers(IMarker.PROBLEM, true, 0)) {
				sb.append("line ").append(marker.getAttribute(IMarker.LINE_NUMBER, 0))
						.append(": ").append(marker.getAttribute(IMarker.MESSAGE, "")) //$NON-NLS-1$ //$NON-NLS-2$
						.append('\n');
			}
		} catch (Exception e) {
			// markers unavailable — proceed without them
		}
		return sb.toString();
	}
}
