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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;

import com.lmscode.ai.Activator;
import com.lmscode.ai.core.FixFinding;

/**
 * LMS Response view: fix details from the AI with file name, line number,
 * severity and the concrete fix. Styled like a code editor (dark background,
 * text-editor font). While a request is running the content is grayed out, a
 * waiting message is shown and the Stop toolbar action cancels the request.
 * Double-click opens the file at the line.
 */
public class ResponseView extends ViewPart {

	public static final String ID = "com.lmscode.ai.views.responseView"; //$NON-NLS-1$

	private Label statusLabel;
	private TableViewer viewer;
	private Text details;
	private String rawResponse = ""; //$NON-NLS-1$

	private Color background;
	private Color foreground;
	private Color dimForeground;

	private Action stopAction;
	private final List<Job> activeJobs = new ArrayList<>();

	@Override
	public void createPartControl(Composite parent) {
		background = new Color(parent.getDisplay(), 30, 30, 30);      // editor-like dark
		foreground = new Color(parent.getDisplay(), 212, 212, 212);   // light code text
		dimForeground = new Color(parent.getDisplay(), 140, 140, 140);
		Font codeFont = JFaceResources.getTextFont(); // the workbench text-editor font/size

		Composite root = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.verticalSpacing = 0;
		root.setLayout(layout);
		root.setBackground(background);

		statusLabel = new Label(root, SWT.NONE);
		statusLabel.setText(" Use the LMS Code context menu (Fix Issues, Compile, Refactor) to populate this view.");
		statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		statusLabel.setBackground(background);
		statusLabel.setForeground(dimForeground);
		statusLabel.setFont(codeFont);

		SashForm sash = new SashForm(root, SWT.VERTICAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		sash.setBackground(background);

		viewer = new TableViewer(sash, SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(false);
		viewer.getTable().setBackground(background);
		viewer.getTable().setForeground(foreground);
		viewer.getTable().setFont(codeFont);
		viewer.setContentProvider(ArrayContentProvider.getInstance());

		column("File", 280, FixFinding::file);
		column("Line", 60, FixFinding::lineLabel);
		column("Severity", 90, FixFinding::severityOrEmpty);
		column("Problem", 360, FixFinding::title);

		details = new Text(sash, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
		details.setBackground(background);
		details.setForeground(foreground);
		details.setFont(codeFont);

		sash.setWeights(new int[] { 55, 45 });

		viewer.addSelectionChangedListener(event -> {
			IStructuredSelection selection = viewer.getStructuredSelection();
			if (selection.getFirstElement() instanceof FixFinding finding) {
				details.setText(render(finding));
			} else {
				details.setText(rawResponse);
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

	private static String render(FixFinding finding) {
		StringBuilder sb = new StringBuilder();
		sb.append(finding.file());
		if (finding.line() > 0) {
			sb.append(" : line ").append(finding.line());
		}
		if (!finding.severityOrEmpty().isBlank()) {
			sb.append("  [").append(finding.severity()).append(']');
		}
		sb.append('\n');
		if (!blank(finding.title())) {
			sb.append('\n').append(finding.title()).append('\n');
		}
		if (!blank(finding.description())) {
			sb.append('\n').append(finding.description()).append('\n');
		}
		if (!blank(finding.fix())) {
			sb.append("\nFix:\n").append(finding.fix()).append('\n');
		}
		return sb.toString();
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}

	private void column(String title, int width, Function<FixFinding, String> accessor) {
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
				return background;
			}

			@Override
			public Color getForeground(Object element) {
				return foreground;
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
		if (background != null) {
			background.dispose();
		}
		if (foreground != null) {
			foreground.dispose();
		}
		if (dimForeground != null) {
			dimForeground.dispose();
		}
		super.dispose();
	}

	/* ===== Busy / stop handling (UI thread) ===== */

	/** Enters the waiting state and registers the job so Stop can cancel it. */
	public void setBusy(String what, Job job) {
		if (job != null) {
			activeJobs.add(job);
		}
		statusLabel.setText(" Waiting for response — " + what + " …");
		statusLabel.setForeground(foreground);
		details.setText("Waiting for response from the AI …\n\nUse the Stop button in this view's toolbar to cancel.");
		viewer.setInput(List.of());
		setWaiting(true);
		statusLabel.getParent().layout();
	}

	private void stopAll() {
		for (Job job : activeJobs) {
			job.cancel();
		}
		activeJobs.clear();
		if (!statusLabel.isDisposed()) {
			statusLabel.setText(" Request stopped.");
			statusLabel.setForeground(dimForeground);
			details.setText(""); //$NON-NLS-1$
			setWaiting(false);
			statusLabel.getParent().layout();
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
		activeJobs.clear();
		setWaiting(false);
		rawResponse = raw == null ? "" : raw; //$NON-NLS-1$
		viewer.setInput(findings);
		statusLabel.setForeground(dimForeground);
		if (findings.isEmpty()) {
			statusLabel.setText(" " + source + ": no structured findings (raw response below).");
			details.setText(rawResponse);
		} else {
			statusLabel.setText(" " + source + ": " + findings.size()
					+ " finding(s). Select a row for details, double-click to open the file at the line.");
			details.setText(""); //$NON-NLS-1$
		}
		statusLabel.getParent().layout();
	}

	public void setError(String source, String message) {
		activeJobs.clear();
		setWaiting(false);
		statusLabel.setText(" " + source + ": failed.");
		statusLabel.setForeground(foreground);
		details.setText(message == null ? "Unknown error" : message);
		statusLabel.getParent().layout();
	}
}
