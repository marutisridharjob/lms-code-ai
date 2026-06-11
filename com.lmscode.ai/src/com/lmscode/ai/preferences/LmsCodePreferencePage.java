package com.lmscode.ai.preferences;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.lmscode.ai.Activator;
import com.lmscode.ai.client.AiClient;
import com.lmscode.ai.client.AiClientFactory;
import com.lmscode.ai.client.AiClientSettings;

/**
 * Preferences for the local LM Studio (or any OpenAI / Anthropic compatible) server:
 * provider protocol, host, port, API key, model, timeouts and refactor behavior.
 */
public class LmsCodePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {

	private static final String[] PROVIDER_LABELS = {
			"OpenAI-compatible  (GET /v1/models, POST /v1/chat/completions)",
			"LM Studio native   (GET /api/v1/models, POST /api/v1/chat)",
			"Anthropic / Claude (GET /v1/models, POST /v1/messages)"
	};
	private static final String[] PROVIDER_VALUES = {
			PreferenceConstants.PROVIDER_OPENAI,
			PreferenceConstants.PROVIDER_LMSTUDIO,
			PreferenceConstants.PROVIDER_ANTHROPIC
	};

	private static final String[] AUTH_LABELS = {
			"API key (x-api-key / Bearer header)",
			"Claude Code login (token from local Claude Code sign-in)"
	};
	private static final String[] AUTH_VALUES = {
			PreferenceConstants.AUTH_API_KEY,
			PreferenceConstants.AUTH_CLAUDE_CODE
	};

	private static final String[] APPLY_MODE_LABELS = {
			"Preview changes in a compare editor before applying",
			"Apply automatically (update, format and save files)"
	};
	private static final String[] APPLY_MODE_VALUES = {
			PreferenceConstants.APPLY_MODE_PREVIEW,
			PreferenceConstants.APPLY_MODE_DIRECT
	};

	private Combo providerCombo;
	private Text hostText;
	private Text portText;
	private Text apiKeyText;
	private Combo authCombo;
	private Combo modelCombo;
	private Text timeoutText;
	private Text maxTokensText;
	private Text temperatureText;
	private Combo applyModeCombo;
	private Text mavenExecText;
	private Text gradleExecText;

	public LmsCodePreferencePage() {
		setDescription("Configure the AI server used by LMS Code (LM Studio, or any OpenAI / Anthropic compatible endpoint).");
	}

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite root = new Composite(parent, SWT.NONE);
		root.setLayout(new GridLayout(1, false));
		root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Group server = group(root, "Server");
		providerCombo = labeledCombo(server, "Provider / protocol:", PROVIDER_LABELS);
		hostText = labeledText(server, "Host (name, IP or full URL):", SWT.BORDER);
		portText = labeledText(server, "Port:", SWT.BORDER);
		apiKeyText = labeledText(server, "API key (optional for local servers):", SWT.BORDER | SWT.PASSWORD);
		authCombo = labeledCombo(server, "Anthropic auth:", AUTH_LABELS);
		authCombo.setToolTipText("'Claude Code login' reads the OAuth token stored by Claude Code"
				+ " (macOS Keychain 'Claude Code-credentials' or ~/.claude/.credentials.json)."
				+ " Sign in once via the 'claude' CLI; tokens refresh whenever Claude Code runs."
				+ " Only used by the Anthropic / Claude provider.");

		Composite serverButtons = new Composite(server, SWT.NONE);
		serverButtons.setLayout(new GridLayout(3, false));
		serverButtons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		Button testButton = new Button(serverButtons, SWT.PUSH);
		testButton.setText("Test Connection");
		testButton.addListener(SWT.Selection, e -> testConnection());
		Button claudeCloudButton = new Button(serverButtons, SWT.PUSH);
		claudeCloudButton.setText("Use Claude Cloud");
		claudeCloudButton.setToolTipText("Pre-fill host/port/provider/auth for api.anthropic.com using your Claude Code login");
		claudeCloudButton.addListener(SWT.Selection, e -> {
			select(providerCombo, PROVIDER_VALUES, PreferenceConstants.PROVIDER_ANTHROPIC);
			hostText.setText("https://api.anthropic.com"); //$NON-NLS-1$
			portText.setText("443"); //$NON-NLS-1$
			select(authCombo, AUTH_VALUES, PreferenceConstants.AUTH_CLAUDE_CODE);
		});

		Group model = group(root, "Model");
		Composite modelRow = new Composite(model, SWT.NONE);
		modelRow.setLayout(new GridLayout(3, false));
		modelRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		new Label(modelRow, SWT.NONE).setText("Model:");
		modelCombo = new Combo(modelRow, SWT.DROP_DOWN);
		modelCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Button fetchButton = new Button(modelRow, SWT.PUSH);
		fetchButton.setText("Fetch Models");
		fetchButton.addListener(SWT.Selection, e -> fetchModels());

		Group request = group(root, "Requests");
		timeoutText = labeledText(request, "Wait time for response (minutes):", SWT.BORDER);
		timeoutText.setToolTipText("How long to wait for the AI before reporting that the model is slow. Default: 3 minutes.");
		maxTokensText = labeledText(request, "Max tokens (0 = server default):", SWT.BORDER);
		temperatureText = labeledText(request, "Temperature:", SWT.BORDER);

		Group refactor = group(root, "Refactoring");
		applyModeCombo = labeledCombo(refactor, "When refactoring:", APPLY_MODE_LABELS);

		Group buildTools = group(root, "Build tools (for the Compile action; empty = auto-detect)");
		mavenExecText = labeledText(buildTools, "Maven executable:", SWT.BORDER);
		mavenExecText.setMessage("e.g. /opt/homebrew/bin/mvn");
		gradleExecText = labeledText(buildTools, "Gradle executable:", SWT.BORDER);
		gradleExecText.setMessage("e.g. /opt/homebrew/bin/gradle");

		loadFrom(getPreferenceStore());
		return root;
	}

	private static Group group(Composite parent, String text) {
		Group g = new Group(parent, SWT.NONE);
		g.setText(text);
		g.setLayout(new GridLayout(2, false));
		g.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		return g;
	}

	private static Text labeledText(Composite parent, String label, int style) {
		new Label(parent, SWT.NONE).setText(label);
		Text t = new Text(parent, style);
		t.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		return t;
	}

	private static Combo labeledCombo(Composite parent, String label, String[] items) {
		new Label(parent, SWT.NONE).setText(label);
		Combo c = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
		c.setItems(items);
		c.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		return c;
	}

	private void loadFrom(IPreferenceStore store) {
		select(providerCombo, PROVIDER_VALUES, store.getString(PreferenceConstants.P_PROVIDER));
		hostText.setText(store.getString(PreferenceConstants.P_HOST));
		portText.setText(Integer.toString(store.getInt(PreferenceConstants.P_PORT)));
		apiKeyText.setText(store.getString(PreferenceConstants.P_API_KEY));
		select(authCombo, AUTH_VALUES, store.getString(PreferenceConstants.P_ANTHROPIC_AUTH));
		modelCombo.setText(store.getString(PreferenceConstants.P_MODEL));
		timeoutText.setText(Integer.toString(store.getInt(PreferenceConstants.P_WAIT_MINUTES)));
		maxTokensText.setText(Integer.toString(store.getInt(PreferenceConstants.P_MAX_TOKENS)));
		temperatureText.setText(store.getString(PreferenceConstants.P_TEMPERATURE));
		select(applyModeCombo, APPLY_MODE_VALUES, store.getString(PreferenceConstants.P_REFACTOR_APPLY_MODE));
		mavenExecText.setText(store.getString(PreferenceConstants.P_MAVEN_EXEC));
		gradleExecText.setText(store.getString(PreferenceConstants.P_GRADLE_EXEC));
	}

	private static void select(Combo combo, String[] values, String value) {
		for (int i = 0; i < values.length; i++) {
			if (values[i].equals(value)) {
				combo.select(i);
				return;
			}
		}
		combo.select(0);
	}

	@Override
	protected void performDefaults() {
		IPreferenceStore store = getPreferenceStore();
		select(providerCombo, PROVIDER_VALUES, store.getDefaultString(PreferenceConstants.P_PROVIDER));
		hostText.setText(store.getDefaultString(PreferenceConstants.P_HOST));
		portText.setText(Integer.toString(store.getDefaultInt(PreferenceConstants.P_PORT)));
		apiKeyText.setText(store.getDefaultString(PreferenceConstants.P_API_KEY));
		select(authCombo, AUTH_VALUES, store.getDefaultString(PreferenceConstants.P_ANTHROPIC_AUTH));
		modelCombo.setText(store.getDefaultString(PreferenceConstants.P_MODEL));
		timeoutText.setText(Integer.toString(store.getDefaultInt(PreferenceConstants.P_WAIT_MINUTES)));
		maxTokensText.setText(Integer.toString(store.getDefaultInt(PreferenceConstants.P_MAX_TOKENS)));
		temperatureText.setText(store.getDefaultString(PreferenceConstants.P_TEMPERATURE));
		select(applyModeCombo, APPLY_MODE_VALUES, store.getDefaultString(PreferenceConstants.P_REFACTOR_APPLY_MODE));
		mavenExecText.setText(store.getDefaultString(PreferenceConstants.P_MAVEN_EXEC));
		gradleExecText.setText(store.getDefaultString(PreferenceConstants.P_GRADLE_EXEC));
		super.performDefaults();
	}

	@Override
	public boolean performOk() {
		int port = parseInt(portText.getText(), -1);
		if (port <= 0 || port > 65535) {
			setErrorMessage("Port must be a number between 1 and 65535.");
			return false;
		}
		int waitMinutes = parseInt(timeoutText.getText(), -1);
		if (waitMinutes <= 0 || waitMinutes > 120) {
			setErrorMessage("Wait time must be between 1 and 120 minutes.");
			return false;
		}
		int maxTokens = parseInt(maxTokensText.getText(), -1);
		if (maxTokens < 0) {
			setErrorMessage("Max tokens must be 0 or a positive number.");
			return false;
		}
		setErrorMessage(null);

		IPreferenceStore store = getPreferenceStore();
		store.setValue(PreferenceConstants.P_PROVIDER, PROVIDER_VALUES[Math.max(0, providerCombo.getSelectionIndex())]);
		store.setValue(PreferenceConstants.P_HOST, hostText.getText().trim());
		store.setValue(PreferenceConstants.P_PORT, port);
		store.setValue(PreferenceConstants.P_API_KEY, apiKeyText.getText().trim());
		store.setValue(PreferenceConstants.P_ANTHROPIC_AUTH, AUTH_VALUES[Math.max(0, authCombo.getSelectionIndex())]);
		store.setValue(PreferenceConstants.P_MODEL, modelCombo.getText().trim());
		store.setValue(PreferenceConstants.P_WAIT_MINUTES, waitMinutes);
		store.setValue(PreferenceConstants.P_MAX_TOKENS, maxTokens);
		store.setValue(PreferenceConstants.P_TEMPERATURE, temperatureText.getText().trim());
		store.setValue(PreferenceConstants.P_REFACTOR_APPLY_MODE,
				APPLY_MODE_VALUES[Math.max(0, applyModeCombo.getSelectionIndex())]);
		store.setValue(PreferenceConstants.P_MAVEN_EXEC, mavenExecText.getText().trim());
		store.setValue(PreferenceConstants.P_GRADLE_EXEC, gradleExecText.getText().trim());
		return true;
	}

	private static int parseInt(String s, int fallback) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Settings built from the current (possibly unsaved) field values. */
	private AiClientSettings settingsFromFields() {
		return new AiClientSettings(
				PROVIDER_VALUES[Math.max(0, providerCombo.getSelectionIndex())],
				hostText.getText().trim(),
				parseInt(portText.getText(), 1234),
				apiKeyText.getText().trim(),
				AUTH_VALUES[Math.max(0, authCombo.getSelectionIndex())],
				modelCombo.getText().trim(),
				Math.max(1, parseInt(timeoutText.getText(), 3)) * 60,
				parseInt(maxTokensText.getText(), 0),
				parseDouble(temperatureText.getText(), 0.2d));
	}

	private static double parseDouble(String s, double fallback) {
		try {
			return Double.parseDouble(s.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private void fetchModels() {
		AiClientSettings settings = settingsFromFields();
		Job job = Job.create("LMS Code: fetching models from " + settings.baseUrl(), (IProgressMonitor monitor) -> {
			try {
				AiClient client = AiClientFactory.create(settings);
				List<String> models = client.listModels();
				asyncUi(() -> {
					String current = modelCombo.getText();
					modelCombo.setItems(models.toArray(new String[0]));
					if (!current.isEmpty()) {
						modelCombo.setText(current);
					} else if (!models.isEmpty()) {
						modelCombo.select(0);
					}
					MessageDialog.openInformation(getShell(), "LMS Code AI",
							"Fetched " + models.size() + " model(s) from " + settings.baseUrl());
				});
				return Status.OK_STATUS;
			} catch (Exception e) {
				asyncUi(() -> MessageDialog.openError(getShell(), "LMS Code AI",
						"Could not fetch models from " + settings.baseUrl() + ":\n\n" + e.getMessage()));
				return Status.OK_STATUS;
			}
		});
		job.setUser(true);
		job.schedule();
	}

	private void testConnection() {
		AiClientSettings settings = settingsFromFields();
		Job job = Job.create("LMS Code: testing connection to " + settings.baseUrl(), (IProgressMonitor monitor) -> {
			try {
				AiClient client = AiClientFactory.create(settings);
				List<String> models = client.listModels();
				asyncUi(() -> MessageDialog.openInformation(getShell(), "LMS Code AI",
						"Connected to " + settings.baseUrl() + "\nServer reports " + models.size() + " model(s)."));
			} catch (Exception e) {
				asyncUi(() -> MessageDialog.openError(getShell(), "LMS Code AI",
						"Connection to " + settings.baseUrl() + " failed:\n\n" + e.getMessage()));
			}
			return Status.OK_STATUS;
		});
		job.setUser(true);
		job.schedule();
	}

	private void asyncUi(Runnable r) {
		if (getControl() == null || getControl().isDisposed()) {
			return;
		}
		getControl().getDisplay().asyncExec(() -> {
			if (getControl() != null && !getControl().isDisposed()) {
				r.run();
			}
		});
	}
}
