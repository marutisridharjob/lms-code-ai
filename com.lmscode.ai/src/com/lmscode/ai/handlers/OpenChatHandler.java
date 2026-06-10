package com.lmscode.ai.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

import com.lmscode.ai.views.ChatView;

/**
 * "LMS Code > Chat": opens the LMS Chat view, seeding it with the selected
 * resource path and (when invoked from an editor) the selected text.
 */
public class OpenChatHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection selection = HandlerUtil.getCurrentSelection(event);

		String path = null;
		String snippet = null;

		IResource resource = SelectionUtils.firstResource(selection);
		if (resource != null) {
			path = resource.getFullPath().toString();
		}
		if (selection instanceof ITextSelection textSelection
				&& textSelection.getText() != null && !textSelection.getText().isBlank()) {
			snippet = textSelection.getText();
			if (path == null) {
				IResource editorResource = org.eclipse.core.runtime.Adapters.adapt(
						HandlerUtil.getActiveEditor(event) != null
								? HandlerUtil.getActiveEditor(event).getEditorInput()
								: null,
						IResource.class);
				if (editorResource != null) {
					path = editorResource.getFullPath().toString();
				}
			}
		}

		try {
			IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
			ChatView view = (ChatView) page.showView(ChatView.ID);
			view.setContext(path, snippet);
		} catch (PartInitException e) {
			throw new ExecutionException("Cannot open LMS Chat view", e);
		}
		return null;
	}
}
