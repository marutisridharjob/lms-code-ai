package com.lmscode.ai.views;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.part.ViewPart;

import com.lmscode.ai.client.AiClient;
import com.lmscode.ai.client.AiClientFactory;
import com.lmscode.ai.client.ChatMessage;
import com.lmscode.ai.core.Prompts;

/**
 * LMS Chat view: conversation transcript (every entry stamped with time and
 * location), multi-line input and Send button. The conversation history is
 * kept and sent with each request so the model has context.
 */
public class ChatView extends ViewPart {

	public static final String ID = "com.lmscode.ai.views.chatView"; //$NON-NLS-1$

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$

	private StyledText transcript;
	private Text input;
	private Button sendButton;

	private final List<ChatMessage> history = new ArrayList<>();
	private String contextPath;
	private String contextSnippet;
	private boolean contextSnippetSent;

	@Override
	public void createPartControl(Composite parent) {
		SashForm sash = new SashForm(parent, SWT.VERTICAL);

		transcript = new StyledText(sash, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
		transcript.setAlwaysShowScrollBars(false);

		Composite inputArea = new Composite(sash, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.marginHeight = 2;
		inputArea.setLayout(layout);

		input = new Text(inputArea, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
		input.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		input.setMessage("Ask LMS Code… (Ctrl+Enter to send)");
		input.addListener(SWT.KeyDown, e -> {
			if ((e.stateMask & SWT.MOD1) != 0 && (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR)) {
				e.doit = false;
				sendCurrentInput();
			}
		});

		sendButton = new Button(inputArea, SWT.PUSH);
		sendButton.setText("Send");
		sendButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
		sendButton.addListener(SWT.Selection, e -> sendCurrentInput());

		sash.setWeights(new int[] { 75, 25 });

		Action clear = new Action("Clear Conversation") {
			@Override
			public void run() {
				history.clear();
				transcript.setText(""); //$NON-NLS-1$
				transcript.setStyleRanges(new StyleRange[0]);
				contextSnippetSent = false;
			}
		};
		clear.setToolTipText("Clear the conversation and start fresh");
		getViewSite().getActionBars().getToolBarManager().add(clear);
	}

	@Override
	public void setFocus() {
		if (input != null && !input.isDisposed()) {
			input.setFocus();
		}
	}

	/**
	 * Seeds the chat with a workspace location and optional selected code,
	 * e.g. when opened via the context menu.
	 */
	public void setContext(String path, String snippet) {
		this.contextPath = path;
		if (snippet != null && !snippet.isBlank()) {
			this.contextSnippet = snippet;
			this.contextSnippetSent = false;
		}
		if (path != null) {
			appendInfo("Context set to " + path + (contextSnippet != null ? " (with selected text)" : "")); //$NON-NLS-1$
		}
		setFocus();
	}

	private void sendCurrentInput() {
		String text = input.getText().strip();
		if (text.isEmpty() || !sendButton.isEnabled()) {
			return;
		}
		input.setText(""); //$NON-NLS-1$

		String location = currentLocation();
		String outgoing = text;
		if (contextSnippet != null && !contextSnippetSent) {
			outgoing = text + "\n\nContext from " + (contextPath != null ? contextPath : "the editor")
					+ ":\n```\n" + contextSnippet + "\n```";
			contextSnippetSent = true;
		}

		history.add(ChatMessage.user(outgoing));
		appendEntry("You", location, text);
		setBusy(true);

		List<ChatMessage> snapshot = List.copyOf(history);
		Job job = Job.create("LMS Code: chat request", (IProgressMonitor monitor) -> {
			AiClient client = AiClientFactory.fromPreferences();
			try {
				String reply = client.chat(Prompts.CHAT_SYSTEM, snapshot);
				asyncUi(() -> {
					history.add(ChatMessage.assistant(reply));
					appendEntry("Assistant", client.describe(), reply);
					setBusy(false);
				});
			} catch (Exception e) {
				asyncUi(() -> {
					appendEntry("Error", client.describe(), e.getMessage());
					setBusy(false);
				});
			}
			return Status.OK_STATUS;
		});
		job.setSystem(true);
		job.schedule();
	}

	/** Location stamp for user messages: explicit context, else the active editor's file. */
	private String currentLocation() {
		if (contextPath != null) {
			return contextPath;
		}
		try {
			IEditorPart editor = getSite().getPage().getActiveEditor();
			if (editor != null) {
				IResource resource = Adapters.adapt(editor.getEditorInput(), IResource.class);
				if (resource != null) {
					return resource.getFullPath().toString();
				}
			}
		} catch (RuntimeException e) {
			// no active editor — fall through
		}
		return "workspace"; //$NON-NLS-1$
	}

	private void appendEntry(String who, String location, String body) {
		String header = "[" + LocalDateTime.now().format(TIMESTAMP) + "] [" + location + "] " + who + ":\n";
		int start = transcript.getCharCount();
		transcript.append(header);
		StyleRange bold = new StyleRange(start, header.length(), null, null, SWT.BOLD);
		transcript.setStyleRange(bold);
		transcript.append(body.strip() + "\n\n"); //$NON-NLS-1$
		transcript.setTopIndex(transcript.getLineCount() - 1);
	}

	private void appendInfo(String message) {
		int start = transcript.getCharCount();
		String line = "— " + message + " —\n\n";
		transcript.append(line);
		StyleRange italic = new StyleRange(start, line.length(), null, null, SWT.ITALIC);
		transcript.setStyleRange(italic);
		transcript.setTopIndex(transcript.getLineCount() - 1);
	}

	private void setBusy(boolean busy) {
		if (!sendButton.isDisposed()) {
			sendButton.setEnabled(!busy);
			sendButton.setText(busy ? "…" : "Send");
		}
	}

	private void asyncUi(Runnable r) {
		if (transcript == null || transcript.isDisposed()) {
			return;
		}
		transcript.getDisplay().asyncExec(() -> {
			if (!transcript.isDisposed()) {
				r.run();
			}
		});
	}
}
