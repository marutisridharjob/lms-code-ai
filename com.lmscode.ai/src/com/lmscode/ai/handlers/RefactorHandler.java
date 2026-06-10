package com.lmscode.ai.handlers;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import com.lmscode.ai.Activator;
import com.lmscode.ai.preferences.PreferenceConstants;
import com.lmscode.ai.refactor.RefactorJob;

/**
 * "LMS Code > Refactor": collects code files from the selection (recursing
 * into folders, packages and projects) and schedules the refactoring job.
 */
public class RefactorHandler extends AbstractHandler {

	private static final int CONFIRM_THRESHOLD = 1;

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

		boolean applyDirectly = PreferenceConstants.APPLY_MODE_DIRECT.equals(
				Activator.getDefault().getPreferenceStore()
						.getString(PreferenceConstants.P_REFACTOR_APPLY_MODE));

		if (files.size() >= CONFIRM_THRESHOLD) {
			String mode = applyDirectly
					? "Changes will be applied directly to the files."
					: "Each change opens in a compare editor for review before you save it.";
			boolean ok = MessageDialog.openConfirm(shell, "LMS Code - Refactor",
					"Refactor " + files.size() + " file(s) using the configured AI?\n\n" + mode);
			if (!ok) {
				return null;
			}
		}

		new RefactorJob(files, applyDirectly).schedule();
		return null;
	}
}
