package com.lmscode.ai.core;

import java.util.List;

/**
 * System and user prompt templates for the LMS Code features.
 */
public final class Prompts {

	private Prompts() {
	}

	/* ===================== Findings JSON contract ===================== */

	private static final String FINDINGS_JSON_CONTRACT = """
			Respond with ONLY a JSON array (no markdown fences, no prose). Each element must have:
			  "file"        - workspace path of the file the fix applies to
			  "line"        - line number where the fix should be applied (0 if not line-specific)
			  "severity"    - one of CRITICAL, HIGH, MEDIUM, LOW, ERROR, WARNING, INFO
			  "title"       - one-line summary of the problem
			  "description" - explanation of the problem and root cause
			  "fix"         - the concrete fix: corrected code snippet or exact change to make
			If the analyzed content includes dependency management files (pom.xml, build.gradle,
			build.gradle.kts, settings.gradle, gradle/libs.versions.toml), also report dependencies
			with known vulnerabilities or severely outdated versions as findings, with the fixed
			version and the exact snippet to change in "fix".
			If there is nothing to report return [].""";

	/* ===================== Refactor (batch, multi-file) ===================== */

	public static final String REFACTOR_BATCH_SYSTEM = """
			You are an expert software engineer performing a careful refactoring inside an IDE.
			You will receive one or more source files, each introduced by a line of the form
			=== FILE: <workspace path> ===
			followed by that file's full content.
			Improve readability, naming, structure and remove duplication while strictly preserving
			external behavior, public APIs and cross-file consistency (a rename in one file must be
			reflected in the others you are given).
			Respond with ONLY a JSON array (no markdown fences, no prose). Each element:
			  "file"    - the workspace path exactly as given in the === FILE: === header
			  "content" - the COMPLETE new file content
			Only include files you actually changed. If nothing should change return [].""";

	public static String refactorBatchUser(List<String> paths, String filesBlock) {
		return "Refactor the following " + paths.size() + " file(s). Return only the JSON array of changed files.\n\n"
				+ filesBlock;
	}

	/** Legacy single-file refactor, used for compare-editor previews. */
	public static final String REFACTOR_SYSTEM = """
			You are an expert software engineer performing a careful refactoring inside an IDE.
			Improve readability, naming, structure and remove duplication while strictly preserving \
			the external behavior, public API and file's package/imports validity.
			Respond with ONLY the complete refactored file content.
			Do not add explanations, comments about your changes, or markdown code fences.""";

	public static String refactorUser(String filePath, String code) {
		return "Refactor the following file (" + filePath + "). Return the full file content only.\n\n" + code;
	}

	/* ===================== Fix issues ===================== */

	public static final String FIX_SYSTEM = """
			You are an expert software engineer diagnosing and fixing problems inside an IDE.
			You will receive source files and/or IDE problem reports.
			Identify bugs, compilation problems, code smells and security issues and provide
			concrete fixes with exact file and line locations.
			""" + FINDINGS_JSON_CONTRACT;

	public static String fixFileUser(String filePath, String content, String markers) {
		StringBuilder sb = new StringBuilder();
		sb.append("Analyze this file and report all issues with fixes.\n");
		sb.append("=== FILE: ").append(filePath).append(" ===\n").append(content).append('\n');
		if (markers != null && !markers.isBlank()) {
			sb.append("\nThe IDE currently reports these problems in the file:\n").append(markers);
		}
		return sb.toString();
	}

	public static String fixProblemsUser(String problemsBlock) {
		return "Fix the following IDE problems. Each problem includes its file, line, message and "
				+ "surrounding code.\n\n" + problemsBlock;
	}

	/* ===================== Compile analysis ===================== */

	public static final String COMPILE_SYSTEM = """
			You are an expert build engineer diagnosing a failed or noisy Maven/Gradle build.
			You will receive the build command, exit code and the build output.
			Explain every compilation error and warning and provide concrete fixes with exact
			file and line locations parsed from the build output. Also flag dependency problems
			(missing artifacts, version conflicts, vulnerable or severely outdated dependencies)
			with the exact build-file change to make.
			""" + FINDINGS_JSON_CONTRACT;

	public static String compileUser(String projectName, String command, int exitCode, String output) {
		return "Project: " + projectName + "\nCommand: " + command + "\nExit code: " + exitCode
				+ "\n\nBuild output:\n" + output;
	}

	/* ===================== Dependency analysis ===================== */

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

	/* ===================== Chat ===================== */

	public static final String CHAT_SYSTEM = """
			You are LMS Code, an AI coding assistant embedded in the Eclipse IDE.
			Be concise and practical. When showing code, use the language of the user's project.
			When the user includes file context, ground your answer in it.""";
}
