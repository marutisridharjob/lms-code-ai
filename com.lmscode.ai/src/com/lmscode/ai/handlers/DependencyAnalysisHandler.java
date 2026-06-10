package com.lmscode.ai.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

import com.lmscode.ai.dependency.DependencyAnalysisJob;
import com.lmscode.ai.views.DependencyView;

/**
 * "LMS Code > Dependency": locates the build file (pom.xml, build.gradle,
 * build.gradle.kts) of the selected project(s) and runs the AI vulnerability
 * analysis, with results shown in the LMS Dependency view.
 */
public class DependencyAnalysisHandler extends AbstractHandler {

	private static final String[] BUILD_FILES = {
			"pom.xml", "build.gradle", "build.gradle.kts" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShell(event);
		ISelection selection = HandlerUtil.getCurrentSelection(event);

		List<IFile> buildFiles = findBuildFiles(selection);
		if (buildFiles.isEmpty()) {
			MessageDialog.openInformation(shell, "LMS Code",
					"No pom.xml or Gradle build file found for the selection.");
			return null;
		}

		DependencyView view;
		try {
			IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
			view = (DependencyView) page.showView(DependencyView.ID);
		} catch (PartInitException e) {
			throw new ExecutionException("Cannot open LMS Dependency view", e);
		}

		for (IFile buildFile : buildFiles) {
			view.setAnalyzing(buildFile.getFullPath().toString());
			new DependencyAnalysisJob(buildFile, view).schedule();
		}
		return null;
	}

	private static List<IFile> findBuildFiles(ISelection selection) {
		List<IFile> result = new ArrayList<>();
		// A build file selected directly wins.
		for (IResource resource : SelectionUtils.resources(selection)) {
			if (resource instanceof IFile file && isBuildFile(file.getName()) && !result.contains(file)) {
				result.add(file);
			}
		}
		if (!result.isEmpty()) {
			return result;
		}
		// Otherwise look at the root of each selected project.
		for (IProject project : SelectionUtils.collectProjects(selection)) {
			for (String name : BUILD_FILES) {
				IFile candidate = project.getFile(name);
				if (candidate.exists()) {
					result.add(candidate);
					break;
				}
			}
		}
		return result;
	}

	private static boolean isBuildFile(String name) {
		for (String buildFile : BUILD_FILES) {
			if (buildFile.equals(name)) {
				return true;
			}
		}
		return false;
	}
}
