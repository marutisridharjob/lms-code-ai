package com.lmscode.ai.views;

import java.util.List;
import java.util.function.Function;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
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
 * severity and the concrete fix. Double-click opens the file at the line.
 */
public class ResponseView extends ViewPart {

	public static final String ID = "com.lmscode.ai.views.responseView"; //$NON-NLS-1$

	private Label statusLabel;
	private TableViewer viewer;
	private Text details;
	private String rawResponse = ""; //$NON-NLS-1$

	@Override
	public void createPartControl(Composite parent) {
		Composite root = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		root.setLayout(layout);

		statusLabel = new Label(root, SWT.NONE);
		statusLabel.setText("Use the LMS Code context menu (Fix Issues, Compile, Refactor) to populate this view.");
		statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		SashForm sash = new SashForm(root, SWT.VERTICAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		viewer = new TableViewer(sash, SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(true);
		viewer.setContentProvider(ArrayContentProvider.getInstance());

		column("File", 280, FixFinding::file);
		column("Line", 60, FixFinding::lineLabel);
		column("Severity", 90, FixFinding::severityOrEmpty);
		column("Problem", 360, FixFinding::title);

		details = new Text(sash, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);

		sash.setWeights(new int[] { 60, 40 });

		viewer.addSelectionChangedListener(event -> {
			IStructuredSelection selection = viewer.getStructuredSelection();
			if (selection.getFirstElement() instanceof FixFinding finding) {
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
				details.setText(sb.toString());
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

	/* ===== Called by the jobs (always from the UI thread) ===== */

	public void setBusy(String what) {
		statusLabel.setText("LMS Code: " + what + " …");
		statusLabel.getParent().layout();
	}

	public void setResults(String source, List<FixFinding> findings, String raw) {
		rawResponse = raw == null ? "" : raw; //$NON-NLS-1$
		viewer.setInput(findings);
		if (findings.isEmpty()) {
			statusLabel.setText(source + ": no structured findings (raw response below).");
			details.setText(rawResponse);
		} else {
			statusLabel.setText(source + ": " + findings.size()
					+ " finding(s). Select a row for details, double-click to open the file at the line.");
			details.setText(""); //$NON-NLS-1$
		}
		statusLabel.getParent().layout();
	}

	public void setError(String source, String message) {
		statusLabel.setText(source + ": failed.");
		details.setText(message == null ? "Unknown error" : message);
		statusLabel.getParent().layout();
	}
}
