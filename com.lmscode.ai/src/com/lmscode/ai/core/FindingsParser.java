package com.lmscode.ai.core;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.lmscode.ai.Activator;

/**
 * Lenient parsers for the JSON shapes the prompts ask the model to return.
 */
public final class FindingsParser {

	private FindingsParser() {
	}

	/** Parses {@code [{file,line,severity,title,description,fix}]}; empty list when unparsable. */
	public static List<FixFinding> parseFindings(String response) {
		List<FixFinding> findings = new ArrayList<>();
		JsonArray array = array(response);
		if (array == null) {
			return findings;
		}
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject obj = element.getAsJsonObject();
			findings.add(new FixFinding(
					str(obj, "file"), //$NON-NLS-1$
					intOf(obj, "line"), //$NON-NLS-1$
					str(obj, "severity"), //$NON-NLS-1$
					str(obj, "title"), //$NON-NLS-1$
					str(obj, "description"), //$NON-NLS-1$
					str(obj, "fix"))); //$NON-NLS-1$
		}
		return findings;
	}

	/** One file change suggested by a batch refactoring response. */
	public record FileChange(String file, String content) {
	}

	/** Parses {@code [{file,content}]}; empty list when unparsable. */
	public static List<FileChange> parseFileChanges(String response) {
		List<FileChange> changes = new ArrayList<>();
		JsonArray array = array(response);
		if (array == null) {
			return changes;
		}
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject obj = element.getAsJsonObject();
			String file = str(obj, "file"); //$NON-NLS-1$
			String content = str(obj, "content"); //$NON-NLS-1$
			if (!file.isBlank() && !content.isBlank()) {
				changes.add(new FileChange(file, content));
			}
		}
		return changes;
	}

	private static JsonArray array(String response) {
		String json = CodeExtractor.extractJsonArray(response);
		if (json == null) {
			return null;
		}
		try {
			JsonElement parsed = JsonParser.parseString(json);
			return parsed.isJsonArray() ? parsed.getAsJsonArray() : null;
		} catch (RuntimeException e) {
			Activator.logError("Could not parse AI JSON response", e);
			return null;
		}
	}

	private static String str(JsonObject obj, String name) {
		JsonElement e = obj.get(name);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : ""; //$NON-NLS-1$
	}

	private static int intOf(JsonObject obj, String name) {
		JsonElement e = obj.get(name);
		if (e != null && e.isJsonPrimitive()) {
			try {
				return (int) Double.parseDouble(e.getAsString());
			} catch (NumberFormatException ignored) {
				// fall through
			}
		}
		return 0;
	}
}
