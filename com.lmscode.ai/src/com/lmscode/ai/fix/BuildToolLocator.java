package com.lmscode.ai.fix;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.lmscode.ai.Activator;
import com.lmscode.ai.preferences.PreferenceConstants;

/**
 * Locates Maven/Gradle executables. Eclipse launched from the macOS Dock (or
 * a Linux desktop) inherits a minimal PATH, so a bare "mvn" frequently cannot
 * be resolved. Resolution order:
 *
 * <ol>
 * <li>explicit executable path from preferences</li>
 * <li>project-local wrapper (mvnw / gradlew)</li>
 * <li>well-known install locations: current PATH entries, Homebrew, MacPorts,
 * SDKMAN, M2_HOME / MAVEN_HOME / GRADLE_HOME</li>
 * <li>fallback: run through the user's login shell ({@code $SHELL -lc "…"}),
 * which loads the user's real PATH</li>
 * </ol>
 */
final class BuildToolLocator {

	static final boolean WINDOWS =
			System.getProperty("os.name", "").toLowerCase().contains("win"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	private BuildToolLocator() {
	}

	/** Maven command line for {@code dir} with the given goals, or null if no pom.xml. */
	static List<String> maven(File dir, String goals) {
		if (!new File(dir, "pom.xml").exists()) { //$NON-NLS-1$
			return null;
		}
		List<String> args = new ArrayList<>();
		args.add("-B"); //$NON-NLS-1$
		args.addAll(split(goals, PreferenceConstants.DEFAULT_MAVEN_GOALS));
		return command(dir, WINDOWS ? "mvnw.cmd" : "mvnw", "mvn", "maven", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				PreferenceConstants.P_MAVEN_EXEC, new String[] { "M2_HOME", "MAVEN_HOME" }, args); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Gradle command line for {@code dir} with the given tasks, or null if no Gradle build file. */
	static List<String> gradle(File dir, String tasks) {
		if (!new File(dir, "build.gradle").exists() && !new File(dir, "build.gradle.kts").exists()) { //$NON-NLS-1$ //$NON-NLS-2$
			return null;
		}
		List<String> args = new ArrayList<>();
		args.add("--console=plain"); //$NON-NLS-1$
		args.addAll(split(tasks, PreferenceConstants.DEFAULT_GRADLE_TASKS));
		return command(dir, WINDOWS ? "gradlew.bat" : "gradlew", "gradle", "gradle", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				PreferenceConstants.P_GRADLE_EXEC, new String[] { "GRADLE_HOME" }, args); //$NON-NLS-1$
	}

	private static List<String> split(String actions, String fallback) {
		String effective = actions == null || actions.isBlank() ? fallback : actions;
		List<String> parts = new ArrayList<>();
		for (String part : effective.trim().split("\\s+")) { //$NON-NLS-1$
			if (!part.isBlank()) {
				parts.add(part);
			}
		}
		return parts;
	}

	private static List<String> command(File dir, String wrapperName, String toolName,
			String sdkmanCandidate, String prefKey, String[] homeEnvVars, List<String> args) {
		// 1. Explicit preference
		String preferred = Activator.getDefault().getPreferenceStore().getString(prefKey).trim();
		if (!preferred.isEmpty()) {
			return concat(preferred, args);
		}
		// 2. Project wrapper
		File wrapper = new File(dir, wrapperName);
		if (wrapper.canExecute() || (WINDOWS && wrapper.exists())) {
			return concat(new File(dir, wrapperName).getAbsolutePath(), args);
		}
		// 3. Well-known locations
		String found = findExecutable(toolName, sdkmanCandidate, homeEnvVars);
		if (found != null) {
			return concat(found, args);
		}
		// 4. Login shell fallback — loads the user's real PATH (~/.zprofile etc.)
		if (!WINDOWS) {
			String shell = System.getenv("SHELL"); //$NON-NLS-1$
			if (shell == null || shell.isBlank() || !new File(shell).canExecute()) {
				shell = new File("/bin/zsh").canExecute() ? "/bin/zsh" : "/bin/bash"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			return new ArrayList<>(List.of(shell, "-lc", toolName + " " + String.join(" ", args))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		return concat(toolName, args); // Windows: let CreateProcess search PATH
	}

	private static List<String> concat(String executable, List<String> args) {
		List<String> command = new ArrayList<>();
		command.add(executable);
		command.addAll(args);
		return command;
	}

	private static String findExecutable(String toolName, String sdkmanCandidate, String[] homeEnvVars) {
		String exe = WINDOWS ? toolName + ".cmd" : toolName; //$NON-NLS-1$
		String home = System.getProperty("user.home", ""); //$NON-NLS-1$ //$NON-NLS-2$

		Set<String> directories = new LinkedHashSet<>();
		// Current process PATH first
		String path = System.getenv("PATH"); //$NON-NLS-1$
		if (path != null) {
			for (String entry : path.split(File.pathSeparator)) {
				if (!entry.isBlank()) {
					directories.add(entry);
				}
			}
		}
		// Tool home env vars (M2_HOME, MAVEN_HOME, GRADLE_HOME)
		for (String envVar : homeEnvVars) {
			String value = System.getenv(envVar);
			if (value != null && !value.isBlank()) {
				directories.add(value + File.separator + "bin"); //$NON-NLS-1$
			}
		}
		// Common installs: Homebrew (Apple Silicon + Intel), MacPorts, Linux
		directories.add("/opt/homebrew/bin"); //$NON-NLS-1$
		directories.add("/usr/local/bin"); //$NON-NLS-1$
		directories.add("/opt/local/bin"); //$NON-NLS-1$
		directories.add("/usr/bin"); //$NON-NLS-1$
		// SDKMAN
		directories.add(home + "/.sdkman/candidates/" + sdkmanCandidate + "/current/bin"); //$NON-NLS-1$ //$NON-NLS-2$

		for (String directory : directories) {
			File candidate = new File(directory, exe);
			if (candidate.canExecute()) {
				return candidate.getAbsolutePath();
			}
		}
		return null;
	}
}
