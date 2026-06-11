package com.lmscode.ai.handlers;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

import com.lmscode.ai.fix.CompileJob;
import com.lmscode.ai.fix.CompileOptionsDialog;
import com.lmscode.ai.views.ResponseView;

/**
 * "LMS Code > Compile": runs a deep compile (Maven/Gradle) for the selected
 * project(s), sends the build output to the AI and shows the explained
 * issues with fixes in the LMS Response view.
 */
public class CompileHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		ISelection selection = HandlerUtil.getCurrentSelection(event);

		List<IProject> projects = SelectionUtils.collectProjects(selection);
		if (projects.isEmpty()) {
			MessageDialog.openInformation(shell, "LMS Code",
					"Select a project (or anything inside one) to compile.");
			return null;
		}

		boolean anyMaven = projects.stream().anyMatch(p -> p.getFile("pom.xml").exists()); //$NON-NLS-1$
		boolean anyGradle = projects.stream().anyMatch(
				p -> p.getFile("build.gradle").exists() || p.getFile("build.gradle.kts").exists()); //$NON-NLS-1$ //$NON-NLS-2$
		if (!anyMaven && !anyGradle) {
			MessageDialog.openInformation(shell, "LMS Code",
					"No pom.xml or Gradle build file found in the selected project(s).");
			return null;
		}

		// Pick the goals/tasks; the previous choice is pre-selected.
		CompileOptionsDialog dialog = new CompileOptionsDialog(shell, anyMaven, anyGradle);
		if (dialog.open() != CompileOptionsDialog.OK) {
			return null;
		}

		ResponseView view;
		try {
			IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
			view = (ResponseView) page.showView(ResponseView.ID);
		} catch (PartInitException e) {
			throw new ExecutionException("Cannot open LMS Response view", e);
		}

		for (IProject project : projects) {
			CompileJob job = new CompileJob(project, view, dialog.mavenGoals(), dialog.gradleTasks());
			view.setBusy("compiling " + project.getName(), job);
			job.schedule();
		}
		return null;
	}
}
