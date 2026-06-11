package com.lmscode.ai.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.views.markers.MarkerItem;

import com.lmscode.ai.fix.FixJob;
import com.lmscode.ai.views.ResponseView;

/**
 * "LMS Code > Fix Issues": dual-mode.
 *
 * <ul>
 * <li>In the Problems view (selection contains markers): sends the selected
 * problem details — file, line, message, surrounding code — to the AI.</li>
 * <li>On files/folders: sends each file's whole content (plus its current IDE
 * problem markers) to the AI.</li>
 * </ul>
 *
 * Results appear in the LMS Response view with file/line/fix details.
 */
public class FixIssuesHandler extends AbstractHandler {

	private static final int MAX_PROBLEMS = 25;
	private static final int FILE_CONFIRM_THRESHOLD = 5;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		ISelection selection = HandlerUtil.getCurrentSelection(event);

		ResponseView view = openResponseView(event);

		List<IMarker> markers = markers(selection);
		if (!markers.isEmpty()) {
			if (markers.size() > MAX_PROBLEMS) {
				markers = markers.subList(0, MAX_PROBLEMS);
				MessageDialog.openInformation(shell, "LMS Code",
						"Only the first " + MAX_PROBLEMS + " selected problems will be analyzed.");
			}
			view.setBusy("analyzing " + markers.size() + " problem(s)");
			FixJob.forProblems(markers, view).schedule();
			return null;
		}

		List<IFile> files = filesToAnalyze(selection);
		if (files.isEmpty()) {
			MessageDialog.openInformation(shell, "LMS Code",
					"Select problems in the Problems view, or files/folders to analyze.");
			return null;
		}
		if (files.size() > FILE_CONFIRM_THRESHOLD
				&& !MessageDialog.openConfirm(shell, "LMS Code - Fix Issues",
						"Analyze " + files.size() + " file(s) with the configured AI?")) {
			return null;
		}
		view.setBusy("analyzing " + files.size() + " file(s)");
		FixJob.forFiles(files, view).schedule();
		return null;
	}

	private static List<IMarker> markers(ISelection selection) {
		List<IMarker> markers = new ArrayList<>();
		if (selection instanceof IStructuredSelection structured) {
			for (Object element : structured) {
				IMarker marker = Adapters.adapt(element, IMarker.class);
				if (marker == null && element instanceof MarkerItem item) {
					marker = item.getMarker();
				}
				if (marker != null && marker.exists()) {
					markers.add(marker);
				}
			}
		}
		return markers;
	}

	/** Directly selected files (any type, incl. build files) plus code files from folders. */
	private static List<IFile> filesToAnalyze(ISelection selection) {
		List<IFile> files = new ArrayList<>();
		for (IResource resource : SelectionUtils.resources(selection)) {
			if (resource instanceof IFile file && !files.contains(file)) {
				files.add(file);
			}
		}
		if (files.isEmpty()) {
			files.addAll(SelectionUtils.collectCodeFiles(selection));
		}
		return files;
	}

	private static ResponseView openResponseView(ExecutionEvent event) throws ExecutionException {
		try {
			IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
			return (ResponseView) page.showView(ResponseView.ID);
		} catch (PartInitException e) {
			throw new ExecutionException("Cannot open LMS Response view", e);
		}
	}
}
