package com.lmscode.ai.core;

/**
 * System and user prompt templates for the three features.
 */
public final class Prompts {

	private Prompts() {
	}

	public static final String REFACTOR_SYSTEM = """
			You are an expert software engineer performing a careful refactoring inside an IDE.
			Improve readability, naming, structure and remove duplication while strictly preserving \
			the external behavior, public API and file's package/imports validity.
			Respond with ONLY the complete refactored file content.
			Do not add explanations, comments about your changes, or markdown code fences.""";

	public static String refactorUser(String filePath, String code) {
		return "Refactor the following file (" + filePath + "). Return the full file content only.\n\n" + code;
	}

	public static final String DEPENDENCY_SYSTEM = """
			You are a software supply-chain security analyst.
			You will receive the content of a build file (Maven pom.xml or Gradle build script).
			Identify declared dependencies with known vulnerabilities or severely outdated versions \
			and suggest fixed versions.
			Respond with ONLY a JSON array (no markdown fences, no prose). Each element must have \
			these string fields:
			  "dependency"     - group/artifact or module name
			  "currentVersion" - version found in the build file ("" if managed elsewhere)
			  "severity"       - one of CRITICAL, HIGH, MEDIUM, LOW, INFO
			  "issue"          - short description of the vulnerability or problem
			  "cve"            - CVE/GHSA id(s) if known, otherwise ""
			  "fixedVersion"   - recommended safe version
			  "recommendation" - the exact snippet to change in the build file
			If nothing is wrong return [].""";

	public static String dependencyUser(String buildFilePath, String content) {
		return "Analyze this build file (" + buildFilePath + ") for vulnerable or outdated dependencies:\n\n"
				+ content;
	}

	public static final String CHAT_SYSTEM = """
			You are LMS Code, an AI coding assistant embedded in the Eclipse IDE.
			Be concise and practical. When showing code, use the language of the user's project.
			When the user includes file context, ground your answer in it.""";
}
