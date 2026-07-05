package com.lmscode.ai.fix;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;

import com.lmscode.ai.Activator;
import com.lmscode.ai.client.AiClient;
import com.lmscode.ai.client.AiClientFactory;
import com.lmscode.ai.client.ChatMessage;
import com.lmscode.ai.core.FindingsParser;
import com.lmscode.ai.core.FixFinding;
import com.lmscode.ai.core.Prompts;
import com.lmscode.ai.views.ResponseView;

/**
 * Runs a deep compile with the project's build tool (Maven wrapper/mvn or
 * Gradle wrapper/gradle), sends the build output to the AI and shows the
 * explained compilation/dependency issues in the LMS Response view.
 */
public class CompileJob extends Job {

	private static final int BUILD_TIMEOUT_MINUTES = 15;
	/** Build output sent to the model is capped to this many trailing characters. */
	private static final int OUTPUT_TAIL_CHARS = 16_000;

	private final IProject project;
	private final ResponseView view;
	private final String mavenGoals;
	private final String gradleTasks;

	public CompileJob(IProject project, ResponseView view, String mavenGoals, String gradleTasks) {
		super("LMS Code: compiling " + project.getName());
		this.project = project;
		this.view = view;
		this.mavenGoals = mavenGoals;
		this.gradleTasks = gradleTasks;
		setUser(true);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		String source = "Compile (" + project.getName() + ")";
		try {
			File dir = project.getLocation() == null ? null : project.getLocation().toFile();
			if (dir == null) {
				throw new IOException("Project location is not on the local file system.");
			}
			List<String> command = buildCommand(dir);
			if (command == null) {
				throw new IOException("No pom.xml or Gradle build file found in " + project.getName() + ".");
			}

			monitor.subTask(String.join(" ", command)); //$NON-NLS-1$
			ProcessResult result = execute(command, dir, monitor);
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}

			String output = result.output();
			if (output.length() > OUTPUT_TAIL_CHARS) {
				output = "…(truncated)…\n" + output.substring(output.length() - OUTPUT_TAIL_CHARS);
			}

			monitor.subTask("Analyzing build output with LMS Code");
			AiClient client = AiClientFactory.fromPreferences();
			String response = client.chat(Prompts.COMPILE_SYSTEM,
					List.of(ChatMessage.user(Prompts.compileUser(
							project.getName(), String.join(" ", command), result.exitCode(), output)))); //$NON-NLS-1$
			List<FixFinding> findings = FindingsParser.parseFindings(response);

			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS; // stopped — don't push results into the view
			}
			String summary = source + " — exit code " + result.exitCode();
			Display.getDefault().asyncExec(() -> view.setResults(summary, findings, response));
			return Status.OK_STATUS;
		} catch (Exception e) {
			Activator.logError("Compile analysis failed for " + project.getName(), e);
			String message = e.getMessage()
					+ "\n\nHint: a project-local wrapper (mvnw / gradlew) is used when present; otherwise"
					+ " mvn/gradle are searched on the PATH, in Homebrew/MacPorts/SDKMAN locations and"
					+ " M2_HOME/MAVEN_HOME/GRADLE_HOME, then run via your login shell. If the tool still"
					+ " isn't found, set its full path under Preferences > LMS Code AI > Build tools"
					+ " (e.g. /opt/homebrew/bin/mvn — find yours with 'which mvn' in Terminal).";
			Display.getDefault().asyncExec(() -> view.setError(source, message));
			return Status.OK_STATUS; // surfaced in the view
		}
	}

	/** Resolved Maven/Gradle command via {@link BuildToolLocator}; null when no build file exists. */
	private List<String> buildCommand(File dir) {
		List<String> maven = BuildToolLocator.maven(dir, mavenGoals);
		if (maven != null) {
			return maven;
		}
		return BuildToolLocator.gradle(dir, gradleTasks);
	}

	private record ProcessResult(int exitCode, String output) {
	}

	private static ProcessResult execute(List<String> command, File dir, IProgressMonitor monitor)
			throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(command)
				.directory(dir)
				.redirectErrorStream(true);
		// Eclipse.app on macOS often lacks Homebrew paths; make mvn/gradle findable.
		String path = pb.environment().getOrDefault("PATH", ""); //$NON-NLS-1$ //$NON-NLS-2$
		pb.environment().put("PATH", path + File.pathSeparator //$NON-NLS-1$
				+ "/opt/homebrew/bin" + File.pathSeparator + "/usr/local/bin"); //$NON-NLS-1$ //$NON-NLS-2$

		Process process = pb.start();
		StringBuilder output = new StringBuilder();
		Thread reader = new Thread(() -> {
			try (BufferedReader in = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = in.readLine()) != null) {
					synchronized (output) {
						output.append(line).append('\n');
					}
				}
			} catch (IOException ignored) {
				// stream closed when the process ends
			}
		}, "lms-code-build-output"); //$NON-NLS-1$
		reader.setDaemon(true);
		reader.start();

		long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(BUILD_TIMEOUT_MINUTES);
		while (process.isAlive()) {
			if (monitor.isCanceled() || System.nanoTime() > deadline) {
				process.destroyForcibly();
				process.waitFor(10, TimeUnit.SECONDS);
				if (!monitor.isCanceled()) {
					synchronized (output) {
						output.append("\n[LMS Code] Build timed out after ")
								.append(BUILD_TIMEOUT_MINUTES).append(" minutes and was stopped.\n");
					}
				}
				break;
			}
			process.waitFor(500, TimeUnit.MILLISECONDS);
		}
		reader.join(5_000);
		synchronized (output) {
			return new ProcessResult(process.isAlive() ? -1 : process.exitValue(), output.toString());
		}
	}
}
