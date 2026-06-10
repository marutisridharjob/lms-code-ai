package com.lmscode.ai.refactor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.ResourceNode;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.graphics.Image;

/**
 * Compare editor input showing the AI proposal (left, read-only) against the
 * current file (right, editable). Saving the editor writes the merged right
 * side back to the file.
 */
public class RefactorCompareInput extends CompareEditorInput {

	private final IFile file;
	private final String proposedContent;
	private EditableResourceNode rightNode;

	public RefactorCompareInput(IFile file, String proposedContent) {
		super(new CompareConfiguration());
		this.file = file;
		this.proposedContent = proposedContent;
		CompareConfiguration configuration = getCompareConfiguration();
		configuration.setLeftLabel("LMS Code proposal");
		configuration.setRightLabel("Current: " + file.getFullPath().toString());
		configuration.setLeftEditable(false);
		configuration.setRightEditable(true);
		setTitle("LMS Refactor: " + file.getName());
	}

	@Override
	protected Object prepareInput(IProgressMonitor monitor) {
		ITypedElement left = new ProposedNode(file, proposedContent);
		rightNode = new EditableResourceNode(file);
		return new Differencer().findDifferences(false, monitor, null, null, left, rightNode);
	}

	@Override
	public void saveChanges(IProgressMonitor monitor) throws CoreException {
		super.saveChanges(monitor);
		if (rightNode != null) {
			rightNode.commit(monitor);
		}
		setDirty(false);
	}

	/** In-memory node holding the AI proposal. */
	private static final class ProposedNode implements ITypedElement, IStreamContentAccessor {

		private final IFile file;
		private final String content;

		ProposedNode(IFile file, String content) {
			this.file = file;
			this.content = content;
		}

		@Override
		public String getName() {
			return file.getName();
		}

		@Override
		public Image getImage() {
			return null;
		}

		@Override
		public String getType() {
			String extension = file.getFileExtension();
			return extension != null ? extension : ITypedElement.TEXT_TYPE;
		}

		@Override
		public InputStream getContents() {
			return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
		}
	}

	/** Resource node that can write the merged buffer back to the file. */
	private static final class EditableResourceNode extends ResourceNode {

		private boolean dirty;

		EditableResourceNode(IFile file) {
			super(file);
		}

		@Override
		public void setContent(byte[] contents) {
			dirty = true;
			super.setContent(contents);
		}

		void commit(IProgressMonitor monitor) throws CoreException {
			if (!dirty) {
				return;
			}
			byte[] bytes = getContent();
			IFile target = (IFile) getResource();
			target.setContents(new ByteArrayInputStream(bytes == null ? new byte[0] : bytes),
					true, true, monitor);
			dirty = false;
		}
	}
}
