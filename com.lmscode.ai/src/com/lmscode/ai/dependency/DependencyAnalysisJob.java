package com.lmscode.ai.dependency;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lmscode.ai.Activator;
import com.lmscode.ai.client.AiClient;
import com.lmscode.ai.client.AiClientFactory;
import com.lmscode.ai.client.ChatMessage;
import com.lmscode.ai.core.CodeExtractor;
import com.lmscode.ai.core.Prompts;
import com.lmscode.ai.views.DependencyView;

/**
 * Reads a build file (pom.xml / build.gradle[.kts]), asks the AI for a
 * vulnerability analysis as JSON and pushes the findings into the
 * LMS Dependency view.
 */
public class DependencyAnalysisJob extends Job {

	private final IFile buildFile;
	private final DependencyView view;

	public DependencyAnalysisJob(IFile buildFile, DependencyView view) {
		super("LMS Code: analyzing dependencies of " + buildFile.getProject().getName());
		this.buildFile = buildFile;
		this.view = view;
		setUser(true);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		String label = buildFile.getFullPath().toString();
		try {
			String content;
			try (InputStream in = buildFile.getContents(true)) {
				content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
			AiClient client = AiClientFactory.fromPreferences();
			String response = client.chat(Prompts.DEPENDENCY_SYSTEM,
					List.of(ChatMessage.user(Prompts.dependencyUser(label, content))));
			List<DependencyFinding> findings = parse(response);
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS; // stopped — don't push results into the view
			}
			Display.getDefault().asyncExec(() -> view.setResults(label, findings, response));
			return Status.OK_STATUS;
		} catch (Exception e) {
			Activator.logError("Dependency analysis failed for " + label, e);
			Display.getDefault().asyncExec(() -> view.setError(label, e.getMessage()));
			return Status.OK_STATUS; // error already surfaced in the view
		}
	}

	private static List<DependencyFinding> parse(String response) {
		List<DependencyFinding> findings = new ArrayList<>();
		String json = CodeExtractor.extractJsonArray(response);
		if (json == null) {
			return findings;
		}
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonArray()) {
				return findings;
			}
			JsonArray array = parsed.getAsJsonArray();
			for (JsonElement element : array) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject obj = element.getAsJsonObject();
				findings.add(new DependencyFinding(
						str(obj, "dependency"), //$NON-NLS-1$
						str(obj, "currentVersion"), //$NON-NLS-1$
						str(obj, "severity"), //$NON-NLS-1$
						str(obj, "issue"), //$NON-NLS-1$
						str(obj, "cve"), //$NON-NLS-1$
						str(obj, "fixedVersion"), //$NON-NLS-1$
						str(obj, "recommendation"))); //$NON-NLS-1$
			}
		} catch (RuntimeException e) {
			Activator.logError("Could not parse dependency findings JSON", e);
		}
		return findings;
	}

	private static String str(JsonObject obj, String name) {
		JsonElement e = obj.get(name);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : ""; //$NON-NLS-1$
	}
}
