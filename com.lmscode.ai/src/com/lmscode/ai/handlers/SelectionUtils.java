package com.lmscode.ai.handlers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import com.lmscode.ai.Activator;

/**
 * Resolves workbench selections (Project/Package Explorer entries, editors,
 * JDT elements via adapters) to workspace resources.
 */
public final class SelectionUtils {

	/** File extensions treated as refactorable source code. */
	private static final Set<String> CODE_EXTENSIONS = Set.of(
			"java", "kt", "kts", "groovy", "scala", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			"js", "jsx", "ts", "tsx", "mjs", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			"py", "rb", "go", "rs", "php", "swift", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
			"c", "cc", "cpp", "h", "hpp", "cs", "sql"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$

	/** Folder names skipped while recursing. */
	private static final Set<String> IGNORED_FOLDERS = Set.of(
			"target", "build", "bin", "out", "node_modules", ".git", ".settings"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$

	private SelectionUtils() {
	}

	/** All resources adaptable from the selection (files, folders, projects, JDT elements...). */
	public static List<IResource> resources(ISelection selection) {
		List<IResource> result = new ArrayList<>();
		if (selection instanceof IStructuredSelection structured) {
			for (Object element : structured) {
				IResource resource = Adapters.adapt(element, IResource.class);
				if (resource != null) {
					result.add(resource);
				}
			}
		}
		return result;
	}

	/**
	 * Collects code files from the selection, recursing into folders,
	 * packages and projects.
	 */
	public static List<IFile> collectCodeFiles(ISelection selection) {
		Set<IFile> files = new LinkedHashSet<>();
		for (IResource resource : resources(selection)) {
			collect(resource, files);
		}
		return new ArrayList<>(files);
	}

	private static void collect(IResource resource, Set<IFile> into) {
		if (resource instanceof IFile file) {
			if (isCodeFile(file)) {
				into.add(file);
			}
		} else if (resource instanceof IContainer container) {
			if (container instanceof IProject project && !project.isOpen()) {
				return;
			}
			if (IGNORED_FOLDERS.contains(container.getName())) {
				return;
			}
			try {
				for (IResource member : container.members()) {
					collect(member, into);
				}
			} catch (CoreException e) {
				Activator.logError("Cannot list members of " + container.getFullPath(), e);
			}
		}
	}

	public static boolean isCodeFile(IFile file) {
		String extension = file.getFileExtension();
		return extension != null && CODE_EXTENSIONS.contains(extension.toLowerCase());
	}

	/** Distinct projects of the selected elements. */
	public static List<IProject> collectProjects(ISelection selection) {
		Set<IProject> projects = new LinkedHashSet<>();
		for (IResource resource : resources(selection)) {
			IProject project = resource.getProject();
			if (project != null && project.isOpen()) {
				projects.add(project);
			}
		}
		return new ArrayList<>(projects);
	}

	public static IResource firstResource(ISelection selection) {
		List<IResource> all = resources(selection);
		return all.isEmpty() ? null : all.get(0);
	}
}
