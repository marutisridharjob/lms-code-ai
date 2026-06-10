package com.lmscode.ai.views;

import java.util.List;
import java.util.function.Function;

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
import org.eclipse.ui.part.ViewPart;

import com.lmscode.ai.dependency.DependencyFinding;

/**
 * LMS Dependency view: table of AI-reported vulnerability findings for a
 * build file, with the suggested fix / raw analysis below.
 */
public class DependencyView extends ViewPart {

	public static final String ID = "com.lmscode.ai.views.dependencyView"; //$NON-NLS-1$

	private Label statusLabel;
	private TableViewer viewer;
	private Text details;
	private String rawAnalysis = ""; //$NON-NLS-1$

	@Override
	public void createPartControl(Composite parent) {
		Composite root = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		root.setLayout(layout);

		statusLabel = new Label(root, SWT.NONE);
		statusLabel.setText("Right-click a project and choose LMS Code > Dependency to analyze its build file.");
		statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		SashForm sash = new SashForm(root, SWT.VERTICAL);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		viewer = new TableViewer(sash, SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLinesVisible(true);
		viewer.setContentProvider(ArrayContentProvider.getInstance());

		column("Dependency", 240, DependencyFinding::dependency);
		column("Version", 100, DependencyFinding::currentVersion);
		column("Severity", 90, DependencyFinding::severityOrEmpty);
		column("Issue / CVE", 300, f -> joinIssue(f));
		column("Fixed Version", 110, DependencyFinding::fixedVersion);

		details = new Text(sash, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);

		sash.setWeights(new int[] { 65, 35 });

		viewer.addSelectionChangedListener(event -> {
			IStructuredSelection selection = viewer.getStructuredSelection();
			if (selection.getFirstElement() instanceof DependencyFinding finding) {
				StringBuilder sb = new StringBuilder();
				sb.append(finding.dependency());
				if (!finding.currentVersion().isBlank()) {
					sb.append(' ').append(finding.currentVersion());
				}
				sb.append('\n');
				if (!finding.severityOrEmpty().isBlank()) {
					sb.append("Severity: ").append(finding.severity()).append('\n');
				}
				if (!finding.cve().isBlank()) {
					sb.append("CVE: ").append(finding.cve()).append('\n');
				}
				if (!finding.issue().isBlank()) {
					sb.append('\n').append(finding.issue()).append('\n');
				}
				if (!finding.recommendation().isBlank()) {
					sb.append("\nSuggested fix:\n").append(finding.recommendation()).append('\n');
				}
				details.setText(sb.toString());
			} else {
				details.setText(rawAnalysis);
			}
		});
	}

	private static String joinIssue(DependencyFinding f) {
		if (f.cve() == null || f.cve().isBlank()) {
			return f.issue();
		}
		if (f.issue() == null || f.issue().isBlank()) {
			return f.cve();
		}
		return f.cve() + " — " + f.issue(); //$NON-NLS-1$
	}

	private void column(String title, int width, Function<DependencyFinding, String> accessor) {
		TableViewerColumn column = new TableViewerColumn(viewer, SWT.LEAD);
		column.getColumn().setText(title);
		column.getColumn().setWidth(width);
		column.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof DependencyFinding finding) {
					String value = accessor.apply(finding);
					return value == null ? "" : value; //$NON-NLS-1$
				}
				return ""; //$NON-NLS-1$
			}
		});
	}

	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	public void setAnalyzing(String buildFilePath) {
		statusLabel.setText("Analyzing " + buildFilePath + " …");
		statusLabel.getParent().layout();
	}

	public void setResults(String buildFilePath, List<DependencyFinding> findings, String raw) {
		rawAnalysis = raw == null ? "" : raw; //$NON-NLS-1$
		viewer.setInput(findings);
		if (findings.isEmpty()) {
			statusLabel.setText(buildFilePath + ": no structured findings (raw analysis below).");
			details.setText(rawAnalysis);
		} else {
			statusLabel.setText(buildFilePath + ": " + findings.size() + " finding(s). Select a row for details.");
			details.setText(""); //$NON-NLS-1$
		}
		statusLabel.getParent().layout();
	}

	public void setError(String buildFilePath, String message) {
		statusLabel.setText(buildFilePath + ": analysis failed.");
		details.setText(message == null ? "Unknown error" : message);
		statusLabel.getParent().layout();
	}
}
