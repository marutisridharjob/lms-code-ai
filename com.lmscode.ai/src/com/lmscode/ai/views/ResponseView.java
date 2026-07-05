package com.lmscode.ai.views;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;

import com.lmscode.ai.Activator;
import com.lmscode.ai.core.FixFinding;
import com.lmscode.ai.ui.DarkTheme;
import com.lmscode.ai.ui.MarkdownRenderer;

/**
 * LMS Response view: fix details from the AI with file name, line number,
 * severity and the concrete fix, drafted as formatted rich text (headings,
 * bullets, code blocks) on a dark editor-style surface. While a request runs
 * the content is grayed out, a waiting message is shown and Stop cancels the
 * request. Double-click opens the file at the line.
 */
public class ResponseView extends ViewPart {

	public static final String ID = "com.lmscode.ai.views.responseView"; //$NON-NLS-1$

	private DarkTheme theme;
	private Label statusLabel;
	private TableViewer viewer;
	private StyledText details;
	private String rawResponse = ""; //$NON-NLS-1$

	private Action stopAction;
	private final List<Job> activeJobs = new ArrayList<>();

	@Override
	public void createPartControl(Composite parent) {
		theme = new DarkTheme(parent.getDisplay());

		Composite root = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.verticalSpacing = 0;
		root.setLayout(layout);
		root.setBackground(theme.background);

		statusLabel = new Label(root, SWT.NONE);
		statusLabel.setText(" Use the LMS Code context menu (Fix Issues, Compile, Refactor) to populate this view.");
		statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		statusLabel.setBackground(theme.background);
		statusLabel.setForeground(theme.dim);
		statusLabel.setFont(JFaceResources.getTextFont());

		SashForm sash = new SashForm(root, SWT.VERTICAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		sash.setBackground(theme.background);

		viewer = new TableViewer(sash, SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(false);
		viewer.getTable().setBackground(theme.background);
		viewer.getTable().setForeground(theme.foreground);
		viewer.getTable().setFont(JFaceResources.getTextFont());
		viewer.setContentProvider(ArrayContentProvider.getInstance());

		column("File", 280, FixFinding::file, null);
		column("Line", 60, FixFinding::lineLabel, null);
		column("Severity", 90, FixFinding::severityOrEmpty, this::severityColor);
		column("Problem", 360, FixFinding::title, null);

		details = new StyledText(sash, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
		details.setBackground(theme.background);
		details.setForeground(theme.foreground);
		details.setFont(JFaceResources.getTextFont());
		details.setMargins(10, 8, 10, 8);

		sash.setWeights(new int[] { 55, 45 });

		viewer.addSelectionChangedListener(event -> {
			IStructuredSelection selection = viewer.getStructuredSelection();
			if (selection.getFirstElement() instanceof FixFinding finding) {
				setDetails(renderMarkdown(finding));
			} else {
				setDetails(rawResponse);
			}
		});

		viewer.addDoubleClickListener(event -> {
			IStructuredSelection selection = viewer.getStructuredSelection();
			if (selection.getFirstElement() instanceof FixFinding finding) {
				openAt(finding);
			}
		});

		stopAction = new Action("Stop") {
			@Override
			public void run() {
				stopAll();
			}
		};
		stopAction.setToolTipText("Stop waiting for the AI response");
		stopAction.setEnabled(false);
		getViewSite().getActionBars().getToolBarManager().add(stopAction);
	}

	private Color severityColor(FixFinding finding) {
		String severity = finding.severityOrEmpty().toUpperCase();
		if (severity.contains("CRITICAL") || severity.contains("HIGH") || severity.contains("ERROR")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return theme.accentError;
		}
		if (severity.contains("MEDIUM") || severity.contains("WARN")) { //$NON-NLS-1$ //$NON-NLS-2$
			return theme.codeFg;
		}
		return theme.accentAssistant;
	}

	/** Renders a finding as markdown so code snippets in "fix" draft nicely. */
	private static String renderMarkdown(FixFinding finding) {
		StringBuilder md = new StringBuilder();
		md.append("### ").append(finding.file()); //$NON-NLS-1$
		if (finding.line() > 0) {
			md.append(" : line ").append(finding.line()); //$NON-NLS-1$
		}
		if (!finding.severityOrEmpty().isBlank()) {
			md.append("  [").append(finding.severity()).append(']'); //$NON-NLS-1$
		}
		md.append('\n');
		if (!blank(finding.title())) {
			md.append("\n**").append(finding.title()).append("**\n"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (!blank(finding.description())) {
			md.append('\n').append(finding.description()).append('\n');
		}
		if (!blank(finding.fix())) {
			md.append("\n**Fix**\n"); //$NON-NLS-1$
			if (finding.fix().contains("```")) { //$NON-NLS-1$
				md.append(finding.fix()).append('\n');
			} else {
				md.append("```\n").append(finding.fix()).append("\n```\n"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return md.toString();
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}

	private void setDetails(String markdown) {
		details.setText(""); //$NON-NLS-1$
		MarkdownRenderer.append(details, markdown == null ? "" : markdown, theme); //$NON-NLS-1$
		details.setTopIndex(0);
	}

	private void column(String title, int width, Function<FixFinding, String> accessor,
			Function<FixFinding, Color> colorProvider) {
		TableViewerColumn column = new TableViewerColumn(viewer, SWT.LEAD);
		column.getColumn().setText(title);
		column.getColumn().setWidth(width);
		column.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof FixFinding finding) {
					String value = accessor.apply(finding);
					return value == null ? "" : value; //$NON-NLS-1$
				}
				return ""; //$NON-NLS-1$
			}

			@Override
			public Color getBackground(Object element) {
				return theme.background;
			}

			@Override
			public Color getForeground(Object element) {
				if (colorProvider != null && element instanceof FixFinding finding) {
					return colorProvider.apply(finding);
				}
				return theme.foreground;
			}
		});
	}

	private void openAt(FixFinding finding) {
		if (blank(finding.file())) {
			return;
		}
		try {
			IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(finding.file()));
			if (!file.exists()) {
				return;
			}
			IEditorPart editor = IDE.openEditor(getSite().getPage(), file);
			if (finding.line() > 0 && editor instanceof ITextEditor textEditor) {
				var document = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
				if (document != null && finding.line() <= document.getNumberOfLines()) {
					var region = document.getLineInformation(finding.line() - 1);
					textEditor.selectAndReveal(region.getOffset(), region.getLength());
				}
			}
		} catch (Exception e) {
			Activator.logError("Cannot open " + finding.file() + " at line " + finding.line(), e);
		}
	}

	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	@Override
	public void dispose() {
		stopAll();
		if (theme != null) {
			theme.dispose();
		}
		super.dispose();
	}

	/* ===== Busy / stop handling (UI thread) ===== */

	/** True once the view has been closed — late job callbacks must not touch widgets. */
	private boolean isClosed() {
		return statusLabel == null || statusLabel.isDisposed();
	}

	/** Enters the waiting state and registers the job so Stop can cancel it. */
	public void setBusy(String what, Job job) {
		if (isClosed()) {
			return;
		}
		if (job != null) {
			activeJobs.add(job);
		}
		statusLabel.setText(" Waiting for response — " + what + " …");
		statusLabel.setForeground(theme.foreground);
		viewer.setInput(List.of());
		setDetails("Waiting for response from the AI …\n\nUse the **Stop** button in this view's toolbar to cancel.");
		setWaiting(true);
		statusLabel.getParent().layout();
	}

	private void stopAll() {
		for (Job job : activeJobs) {
			job.cancel();
		}
		activeJobs.clear();
		if (statusLabel != null && !statusLabel.isDisposed()) {
			statusLabel.setText(" Request stopped.");
			statusLabel.setForeground(theme.dim);
			setDetails(""); //$NON-NLS-1$
			setWaiting(false);
			statusLabel.getParent().layout();
		}
	}

	/** Raises this view when a response lands, even if another view covered it meanwhile. */
	private void bringToFront() {
		try {
			getSite().getPage().bringToTop(this);
		} catch (RuntimeException e) {
			// workbench is closing — nothing to raise
		}
	}

	private void setWaiting(boolean waiting) {
		viewer.getTable().setEnabled(!waiting);   // grayed out while waiting
		details.setEnabled(!waiting);
		if (stopAction != null) {
			stopAction.setEnabled(waiting);
		}
	}

	/* ===== Results (called by the jobs via Display.asyncExec) ===== */

	public void setResults(String source, List<FixFinding> findings, String raw) {
		if (isClosed()) {
			return; // view closed while the job was running
		}
		activeJobs.clear();
		setWaiting(false);
		bringToFront();
		rawResponse = raw == null ? "" : raw; //$NON-NLS-1$
		viewer.setInput(findings);
		statusLabel.setForeground(theme.dim);
		if (findings.isEmpty()) {
			statusLabel.setText(" " + source + ": no structured findings (raw response below).");
			setDetails(rawResponse);
		} else {
			statusLabel.setText(" " + source + ": " + findings.size()
					+ " finding(s). Select a row for details, double-click to open the file at the line.");
			setDetails(""); //$NON-NLS-1$
		}
		statusLabel.getParent().layout();
	}

	public void setError(String source, String message) {
		if (isClosed()) {
			return; // view closed while the job was running
		}
		activeJobs.clear();
		setWaiting(false);
		bringToFront();
		statusLabel.setText(" " + source + ": failed.");
		statusLabel.setForeground(theme.accentError);
		setDetails(message == null ? "Unknown error" : message);
		statusLabel.getParent().layout();
	}
}
