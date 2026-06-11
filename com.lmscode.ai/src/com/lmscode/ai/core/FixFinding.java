package com.lmscode.ai.core;

/**
 * One fix/finding reported by the AI: where it is (file + line) and what to do.
 */
public record FixFinding(
		String file,
		int line,
		String severity,
		String title,
		String description,
		String fix) {

	public String severityOrEmpty() {
		return severity == null ? "" : severity; //$NON-NLS-1$
	}

	public String lineLabel() {
		return line > 0 ? Integer.toString(line) : ""; //$NON-NLS-1$
	}
}
