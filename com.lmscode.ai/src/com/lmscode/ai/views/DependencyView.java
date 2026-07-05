package com.lmscode.ai.views;

import java.util.List;
import java.util.function.Function;

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
import org.eclipse.ui.part.ViewPart;

import com.lmscode.ai.dependency.DependencyFinding;
import com.lmscode.ai.ui.DarkTheme;
import com.lmscode.ai.ui.MarkdownRenderer;

/**
 * LMS Dependency view: AI-reported vulnerability findings for a build file
 * on the shared dark editor-style surface, with the suggested fix drafted
 * as formatted rich text below.
 */
public class DependencyView extends ViewPart {

	public static final String ID = "com.lmscode.ai.views.dependencyView"; //$NON-NLS-1$

	private DarkTheme theme;
	private Label statusLabel;
	private TableViewer viewer;
	private StyledText details;
	private String rawAnalysis = ""; //$NON-NLS-1$

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
		statusLabel.setText(" Right-click a project and choose LMS Code > Dependency to analyze its build file.");
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

		column("Dependency", 240, DependencyFinding::dependency, null);
		column("Version", 100, DependencyFinding::currentVersion, null);
		column("Severity", 90, DependencyFinding::severityOrEmpty, this::severityColor);
		column("Issue / CVE", 300, DependencyView::joinIssue, null);
		column("Fixed Version", 110, DependencyFinding::fixedVersion, f -> theme.accentAssistant);

		details = new StyledText(sash, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
		details.setBackground(theme.background);
		details.setForeground(theme.foreground);
		details.setFont(JFaceResources.getTextFont());
		details.setMargins(10, 8, 10, 8);

		sash.setWeights(new int[] { 60, 40 });

		viewer.addSelectionChangedListener(event -> {
			IStructuredSelection selection = viewer.getStructuredSelection();
			if (selection.getFirstElement() instanceof DependencyFinding finding) {
				setDetails(renderMarkdown(finding));
			} else {
				setDetails(rawAnalysis);
			}
		});
	}

	private Color severityColor(DependencyFinding finding) {
		String severity = finding.severityOrEmpty().toUpperCase();
		if (severity.contains("CRITICAL") || severity.contains("HIGH")) { //$NON-NLS-1$ //$NON-NLS-2$
			return theme.accentError;
		}
		if (severity.contains("MEDIUM")) { //$NON-NLS-1$
			return theme.codeFg;
		}
		return theme.accentAssistant;
	}

	private static String renderMarkdown(DependencyFinding finding) {
		StringBuilder md = new StringBuilder();
		md.append("### ").append(finding.dependency()); //$NON-NLS-1$
		if (!finding.currentVersion().isBlank()) {
			md.append(' ').append(finding.currentVersion());
		}
		if (!finding.severityOrEmpty().isBlank()) {
			md.append("  [").append(finding.severity()).append(']'); //$NON-NLS-1$
		}
		md.append('\n');
		if (!finding.cve().isBlank()) {
			md.append("\n**CVE:** ").append(finding.cve()).append('\n'); //$NON-NLS-1$
		}
		if (!finding.issue().isBlank()) {
			md.append('\n').append(finding.issue()).append('\n');
		}
		if (!finding.fixedVersion().isBlank()) {
			md.append("\n**Fixed version:** `").append(finding.fixedVersion()).append("`\n"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (!finding.recommendation().isBlank()) {
			md.append("\n**Suggested change**\n"); //$NON-NLS-1$
			if (finding.recommendation().contains("```")) { //$NON-NLS-1$
				md.append(finding.recommendation()).append('\n');
			} else {
				md.append("```\n").append(finding.recommendation()).append("\n```\n"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return md.toString();
	}

	private void setDetails(String markdown) {
		details.setText(""); //$NON-NLS-1$
		MarkdownRenderer.append(details, markdown == null ? "" : markdown, theme); //$NON-NLS-1$
		details.setTopIndex(0);
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

	private void column(String title, int width, Function<DependencyFinding, String> accessor,
			Function<DependencyFinding, Color> colorProvider) {
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

			@Override
			public Color getBackground(Object element) {
				return theme.background;
			}

			@Override
			public Color getForeground(Object element) {
				if (colorProvider != null && element instanceof DependencyFinding finding) {
					return colorProvider.apply(finding);
				}
				return theme.foreground;
			}
		});
	}

	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	@Override
	public void dispose() {
		if (theme != null) {
			theme.dispose();
		}
		super.dispose();
	}

	private void bringToFront() {
		try {
			getSite().getPage().bringToTop(this);
		} catch (RuntimeException e) {
			// workbench is closing — nothing to raise
		}
	}

	public void setAnalyzing(String buildFilePath) {
		statusLabel.setText(" Analyzing " + buildFilePath + " …");
		statusLabel.setForeground(theme.foreground);
		statusLabel.getParent().layout();
	}

	/** True once the view has been closed — late job callbacks must not touch widgets. */
	private boolean isClosed() {
		return viewer == null || viewer.getControl().isDisposed();
	}

	public void setResults(String buildFilePath, List<DependencyFinding> findings, String raw) {
		if (isClosed()) {
			return; // view closed while the analysis was running
		}
		bringToFront();
		rawAnalysis = raw == null ? "" : raw; //$NON-NLS-1$
		viewer.setInput(findings);
		statusLabel.setForeground(theme.dim);
		if (findings.isEmpty()) {
			statusLabel.setText(" " + buildFilePath + ": no structured findings (raw analysis below).");
			setDetails(rawAnalysis);
		} else {
			statusLabel.setText(" " + buildFilePath + ": " + findings.size() + " finding(s). Select a row for details.");
			setDetails(""); //$NON-NLS-1$
		}
		statusLabel.getParent().layout();
	}

	public void setError(String buildFilePath, String message) {
		if (isClosed()) {
			return; // view closed while the analysis was running
		}
		bringToFront();
		statusLabel.setText(" " + buildFilePath + ": analysis failed.");
		statusLabel.setForeground(theme.accentError);
		setDetails(message == null ? "Unknown error" : message);
		statusLabel.getParent().layout();
	}
}
