package com.lmscode.ai.views;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.part.ViewPart;

import com.lmscode.ai.client.AiClient;
import com.lmscode.ai.client.AiClientFactory;
import com.lmscode.ai.client.ChatMessage;
import com.lmscode.ai.core.Prompts;
import com.lmscode.ai.ui.DarkTheme;
import com.lmscode.ai.ui.MarkdownRenderer;

/**
 * LMS Chat view: a dark, editor-style conversation surface. Assistant replies
 * are rendered as formatted rich text (headings, bullets, code blocks); every
 * entry carries a timestamp and location. Enter sends (Shift+Enter for a new
 * line), a subtle animation shows while the model is thinking, and Stop
 * cancels the in-flight request.
 */
public class ChatView extends ViewPart {

	public static final String ID = "com.lmscode.ai.views.chatView"; //$NON-NLS-1$

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$
	private static final String[] THINKING_FRAMES = { "●○○", "○●○", "○○●", "○●○" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

	private DarkTheme theme;
	private StyledText transcript;
	private Text input;
	private Button sendButton;
	private Label statusLabel;

	private final List<ChatMessage> history = new ArrayList<>();
	private String contextPath;
	private String contextSnippet;
	private boolean contextSnippetSent;

	private Job currentJob;
	private Action stopAction;
	private boolean waiting;
	private int thinkingFrame;
	/** Bumped on every send/stop so callbacks from stale (stopped) requests are dropped. */
	private int requestGeneration;

	@Override
	public void createPartControl(Composite parent) {
		theme = new DarkTheme(parent.getDisplay());

		Composite root = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.verticalSpacing = 4;
		root.setLayout(layout);
		root.setBackground(theme.background);

		transcript = new StyledText(root, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
		transcript.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		transcript.setBackground(theme.background);
		transcript.setForeground(theme.foreground);
		transcript.setFont(JFaceResources.getTextFont());
		transcript.setMargins(10, 10, 10, 10);
		transcript.setAlwaysShowScrollBars(false);

		statusLabel = new Label(root, SWT.NONE);
		statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		statusLabel.setBackground(theme.background);
		statusLabel.setForeground(theme.dim);
		statusLabel.setFont(JFaceResources.getTextFont());
		statusLabel.setText("  Enter to send · Shift+Enter for a new line");

		Composite inputRow = new Composite(root, SWT.NONE);
		GridLayout inputLayout = new GridLayout(2, false);
		inputLayout.marginWidth = 6;
		inputLayout.marginHeight = 4;
		inputRow.setLayout(inputLayout);
		inputRow.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
		inputRow.setBackground(theme.background);

		input = new Text(inputRow, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
		GridData inputData = new GridData(SWT.FILL, SWT.FILL, true, false);
		inputData.heightHint = 52;
		input.setLayoutData(inputData);
		input.setBackground(theme.surface);
		input.setForeground(theme.foreground);
		input.setFont(JFaceResources.getTextFont());
		input.setMessage("Ask LMS Code…");
		input.addListener(SWT.KeyDown, e -> {
			boolean enter = e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR;
			if (enter && (e.stateMask & SWT.SHIFT) == 0) {
				e.doit = false;
				sendCurrentInput();
			}
		});

		sendButton = new Button(inputRow, SWT.PUSH);
		sendButton.setText("Send  ⏎");
		sendButton.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
		sendButton.addListener(SWT.Selection, e -> sendCurrentInput());

		Action clear = new Action("Clear Conversation") {
			@Override
			public void run() {
				requestGeneration++; // orphan any in-flight callback
				if (currentJob != null) {
					currentJob.cancel();
					currentJob = null;
				}
				setWaiting(false);
				history.clear();
				transcript.setText(""); //$NON-NLS-1$
				contextSnippetSent = false;
				appendInfo("Conversation cleared.");
			}
		};
		clear.setToolTipText("Clear the conversation and start fresh");

		stopAction = new Action("Stop") {
			@Override
			public void run() {
				requestGeneration++; // orphan any in-flight callback
				if (currentJob != null) {
					currentJob.cancel();
					currentJob = null;
				}
				setWaiting(false);
				appendInfo("Request stopped.");
			}
		};
		stopAction.setToolTipText("Stop waiting for the model");
		stopAction.setEnabled(false);

		getViewSite().getActionBars().getToolBarManager().add(stopAction);
		getViewSite().getActionBars().getToolBarManager().add(clear);

		appendInfo("Connected settings are under Preferences > LMS Code AI. Right-click code and choose LMS Code > Chat to add context.");
	}

	@Override
	public void setFocus() {
		if (input != null && !input.isDisposed()) {
			input.setFocus();
		}
	}

	@Override
	public void dispose() {
		if (currentJob != null) {
			currentJob.cancel();
		}
		if (theme != null) {
			theme.dispose();
		}
		super.dispose();
	}

	/** Seeds the chat with a workspace location and optional selected code. */
	public void setContext(String path, String snippet) {
		this.contextPath = path;
		if (snippet != null && !snippet.isBlank()) {
			this.contextSnippet = snippet;
			this.contextSnippetSent = false;
		}
		if (path != null) {
			appendInfo("Context: " + path + (contextSnippet != null ? " (with selected text)" : "")); //$NON-NLS-1$
		}
		setFocus();
	}

	private void sendCurrentInput() {
		if (waiting) {
			return;
		}
		String text = input.getText().strip();
		if (text.isEmpty()) {
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
		appendHeader("You", theme.accentUser, location);
		MarkdownRenderer.appendPlain(transcript, text + "\n\n", theme, theme.foreground, SWT.NORMAL); //$NON-NLS-1$
		setWaiting(true);

		List<ChatMessage> snapshot = List.copyOf(history);
		final int generation = ++requestGeneration;
		Job job = Job.create("LMS Code: chat request", (IProgressMonitor monitor) -> {
			AiClient client = AiClientFactory.fromPreferences();
			try {
				String reply = client.chat(Prompts.CHAT_SYSTEM, snapshot);
				asyncUi(() -> {
					if (generation != requestGeneration) {
						return; // stopped or superseded — drop the stale reply
					}
					history.add(ChatMessage.assistant(reply));
					appendHeader("LMS", theme.accentAssistant, client.describe());
					MarkdownRenderer.append(transcript, reply.strip() + "\n", theme); //$NON-NLS-1$
					MarkdownRenderer.appendPlain(transcript, "\n", theme, null, SWT.NORMAL); //$NON-NLS-1$
					setWaiting(false);
				});
			} catch (Exception e) {
				asyncUi(() -> {
					if (generation != requestGeneration) {
						return; // stopped or superseded — drop the stale error
					}
					appendHeader("Error", theme.accentError, client.describe());
					MarkdownRenderer.appendPlain(transcript, String.valueOf(e.getMessage()) + "\n\n", //$NON-NLS-1$
							theme, theme.accentError, SWT.NORMAL);
					setWaiting(false);
				});
			}
			return Status.OK_STATUS;
		});
		job.setSystem(true);
		currentJob = job;
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

	/** "▍ Who · timestamp · location" — role colored, metadata dim. */
	private void appendHeader(String who, Color roleColor, String location) {
		String bar = "▍ "; //$NON-NLS-1$
		String meta = "  ·  " + LocalDateTime.now().format(TIMESTAMP) + "  ·  " + location + "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		MarkdownRenderer.appendPlain(transcript, bar + who, theme, roleColor, SWT.BOLD);
		MarkdownRenderer.appendPlain(transcript, meta, theme, theme.dim, SWT.NORMAL);
	}

	private void appendInfo(String message) {
		MarkdownRenderer.appendPlain(transcript, "— " + message + " —\n\n", theme, theme.dim, SWT.ITALIC); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void setWaiting(boolean nowWaiting) {
		waiting = nowWaiting;
		if (!sendButton.isDisposed()) {
			sendButton.setEnabled(!nowWaiting);
		}
		if (stopAction != null) {
			stopAction.setEnabled(nowWaiting);
		}
		if (nowWaiting) {
			thinkingFrame = 0;
			animateThinking();
		} else {
			currentJob = null;
			if (!statusLabel.isDisposed()) {
				statusLabel.setText("  Enter to send · Shift+Enter for a new line");
			}
		}
	}

	private void animateThinking() {
		if (!waiting || statusLabel.isDisposed()) {
			return;
		}
		statusLabel.setText("  LMS is thinking " + THINKING_FRAMES[thinkingFrame % THINKING_FRAMES.length]);
		thinkingFrame++;
		statusLabel.getDisplay().timerExec(350, this::animateThinking);
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
