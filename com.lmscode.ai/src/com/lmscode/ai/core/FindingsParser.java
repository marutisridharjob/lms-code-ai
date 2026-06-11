package com.lmscode.ai.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Lenient parsers for the JSON the prompts ask the model to return. Local
 * models frequently wrap JSON in prose or markdown fences, return an object
 * instead of an array, or rename fields — so extraction tries several
 * strategies and field aliases before giving up.
 */
public final class FindingsParser {

	private static final Pattern FENCED_BLOCK = Pattern.compile(
			"```[a-zA-Z0-9_+-]*\\R(.*?)\\R?```", Pattern.DOTALL); //$NON-NLS-1$

	/** Object members that may hold the findings array when the model returns an object. */
	private static final String[] ARRAY_MEMBERS = {
			"findings", "issues", "results", "fixes", "problems", "items", "data", "changes", "files" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
	};

	private FindingsParser() {
	}

	/** Parses fix findings; empty list when nothing JSON-like can be extracted. */
	public static List<FixFinding> parseFindings(String response) {
		List<FixFinding> findings = new ArrayList<>();
		JsonArray array = findArray(response);
		if (array == null) {
			return findings;
		}
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject obj = element.getAsJsonObject();
			FixFinding finding = new FixFinding(
					firstString(obj, "file", "path", "filename", "fileName", "location", "source"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
					firstInt(obj, "line", "lineNumber", "line_number", "lineNo", "startLine"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
					firstString(obj, "severity", "level", "priority", "type"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					firstString(obj, "title", "problem", "issue", "summary", "message", "name"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
					firstString(obj, "description", "details", "detail", "explanation", "reason", "analysis"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
					firstString(obj, "fix", "suggestion", "recommendation", "solution", "fixedCode", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
							"suggestedFix", "change", "code")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			if (!finding.file().isBlank() || !finding.title().isBlank()
					|| !finding.description().isBlank() || !finding.fix().isBlank()) {
				findings.add(finding);
			}
		}
		return findings;
	}

	/** One file change suggested by a batch refactoring response. */
	public record FileChange(String file, String content) {
	}

	/** Parses {@code [{file,content}]}; empty list when unparsable. */
	public static List<FileChange> parseFileChanges(String response) {
		List<FileChange> changes = new ArrayList<>();
		JsonArray array = findArray(response);
		if (array == null) {
			return changes;
		}
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject obj = element.getAsJsonObject();
			String file = firstString(obj, "file", "path", "filename", "fileName"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			String content = firstString(obj, "content", "code", "source", "newContent"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			if (!file.isBlank() && !content.isBlank()) {
				changes.add(new FileChange(file, content));
			}
		}
		return changes;
	}

	/* ===================== extraction strategies ===================== */

	/**
	 * Tries, in order: the whole response, every fenced block, the widest
	 * {@code [...]} span, and the widest {@code {...}} span. Objects are
	 * unwrapped (first array-valued member, or known member names) or — when
	 * they look like a single finding — wrapped into a one-element array.
	 */
	private static JsonArray findArray(String response) {
		if (response == null || response.isBlank()) {
			return null;
		}
		List<String> candidates = new ArrayList<>();
		candidates.add(response);
		Matcher matcher = FENCED_BLOCK.matcher(response);
		while (matcher.find()) {
			candidates.add(matcher.group(1));
		}
		int firstBracket = response.indexOf('[');
		int lastBracket = response.lastIndexOf(']');
		if (firstBracket >= 0 && lastBracket > firstBracket) {
			candidates.add(response.substring(firstBracket, lastBracket + 1));
		}
		int firstBrace = response.indexOf('{');
		int lastBrace = response.lastIndexOf('}');
		if (firstBrace >= 0 && lastBrace > firstBrace) {
			candidates.add(response.substring(firstBrace, lastBrace + 1));
		}
		for (String candidate : candidates) {
			JsonArray array = tryParse(candidate.strip());
			if (array != null) {
				return array;
			}
		}
		return null;
	}

	private static JsonArray tryParse(String candidate) {
		if (candidate.isEmpty() || (candidate.charAt(0) != '[' && candidate.charAt(0) != '{')) {
			return null;
		}
		JsonElement parsed;
		try {
			parsed = JsonParser.parseString(candidate);
		} catch (RuntimeException e) {
			return null;
		}
		if (parsed.isJsonArray()) {
			return parsed.getAsJsonArray();
		}
		if (parsed.isJsonObject()) {
			JsonObject obj = parsed.getAsJsonObject();
			// Known wrapper members first, then any array-valued member.
			for (String member : ARRAY_MEMBERS) {
				JsonElement value = obj.get(member);
				if (value != null && value.isJsonArray()) {
					return value.getAsJsonArray();
				}
			}
			for (String key : obj.keySet()) {
				JsonElement value = obj.get(key);
				if (value.isJsonArray() && !value.getAsJsonArray().isEmpty()
						&& value.getAsJsonArray().get(0).isJsonObject()) {
					return value.getAsJsonArray();
				}
			}
			// A single finding object — wrap it.
			JsonArray single = new JsonArray();
			single.add(obj);
			return single;
		}
		return null;
	}

	private static String firstString(JsonObject obj, String... names) {
		for (String name : names) {
			JsonElement e = obj.get(name);
			if (e != null && e.isJsonPrimitive()) {
				String value = e.getAsString();
				if (!value.isBlank()) {
					return value;
				}
			}
		}
		return ""; //$NON-NLS-1$
	}

	private static int firstInt(JsonObject obj, String... names) {
		for (String name : names) {
			JsonElement e = obj.get(name);
			if (e != null && e.isJsonPrimitive()) {
				try {
					return (int) Double.parseDouble(e.getAsString());
				} catch (NumberFormatException ignored) {
					// try next alias
				}
			}
		}
		return 0;
	}
}
