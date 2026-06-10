package com.lmscode.ai.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts source code from an LLM response that may be wrapped in markdown
 * fences despite instructions not to.
 */
public final class CodeExtractor {

	private static final Pattern FENCED_BLOCK = Pattern.compile(
			"```[a-zA-Z0-9_+-]*\\R(.*?)\\R?```", Pattern.DOTALL); //$NON-NLS-1$

	private CodeExtractor() {
	}

	/**
	 * Returns the largest fenced code block if the response contains any,
	 * otherwise the trimmed response itself.
	 */
	public static String extractCode(String response) {
		if (response == null) {
			return ""; //$NON-NLS-1$
		}
		Matcher matcher = FENCED_BLOCK.matcher(response);
		String best = null;
		while (matcher.find()) {
			String block = matcher.group(1);
			if (best == null || block.length() > best.length()) {
				best = block;
			}
		}
		return (best != null ? best : response).strip() + "\n"; //$NON-NLS-1$
	}

	/**
	 * Returns the JSON array portion of a response: strips fences and anything
	 * before the first '[' / after the last ']'. Returns {@code null} when no
	 * array is present.
	 */
	public static String extractJsonArray(String response) {
		if (response == null) {
			return null;
		}
		String s = response;
		Matcher matcher = FENCED_BLOCK.matcher(s);
		if (matcher.find()) {
			s = matcher.group(1);
		}
		int start = s.indexOf('[');
		int end = s.lastIndexOf(']');
		if (start < 0 || end <= start) {
			return null;
		}
		return s.substring(start, end + 1);
	}
}
