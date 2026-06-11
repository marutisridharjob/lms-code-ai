package com.lmscode.ai.handlers;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

import com.lmscode.ai.Activator;
import com.lmscode.ai.preferences.PreferenceConstants;
import com.lmscode.ai.refactor.RefactorJob;
import com.lmscode.ai.views.ResponseView;

/**
 * "LMS Code > Refactor": collects code files from the selection (recursing
 * into folders, packages and projects), sends their whole content with
 * workspace paths to the AI in batches, and applies the suggested changes —
 * updating, formatting and saving each changed file (or opening compare
 * editors in preview mode). A summary lands in the LMS Response view.
 */
public class RefactorHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		List<IFile> files = SelectionUtils.collectCodeFiles(selection);

		if (files.isEmpty()) {
			MessageDialog.openInformation(shell, "LMS Code",
					"No source files found in the selection. Select a file, package, folder or project containing code.");
			return null;
		}

		boolean applyDirectly = !PreferenceConstants.APPLY_MODE_PREVIEW.equals(
				Activator.getDefault().getPreferenceStore()
						.getString(PreferenceConstants.P_REFACTOR_APPLY_MODE));

		String mode = applyDirectly
				? "Suggested changes will be applied automatically: files are updated, formatted and saved."
				: "Each change opens in a compare editor for review before you save it.";
		boolean ok = MessageDialog.openConfirm(shell, "LMS Code - Refactor",
				"Refactor " + files.size() + " file(s) using the configured AI?\n\n" + mode);
		if (!ok) {
			return null;
		}

		ResponseView view;
		try {
			IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
			view = (ResponseView) page.showView(ResponseView.ID);
		} catch (PartInitException e) {
			throw new ExecutionException("Cannot open LMS Response view", e);
		}
		RefactorJob job = new RefactorJob(files, applyDirectly, view);
		view.setBusy("refactoring " + files.size() + " file(s)", job);
		job.schedule();
		return null;
	}
}
