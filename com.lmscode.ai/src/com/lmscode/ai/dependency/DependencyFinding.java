package com.lmscode.ai.dependency;

/**
 * One vulnerability / outdated-dependency finding produced by the AI analysis.
 */
public record DependencyFinding(
		String dependency,
		String currentVersion,
		String severity,
		String issue,
		String cve,
		String fixedVersion,
		String recommendation) {

	public String severityOrEmpty() {
		return severity == null ? "" : severity; //$NON-NLS-1$
	}
}
