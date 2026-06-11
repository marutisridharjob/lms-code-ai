package com.lmscode.ai.preferences;

/**
 * Preference keys and provider identifiers for LMS Code AI.
 */
public final class PreferenceConstants {

	private PreferenceConstants() {
	}

	/** Provider protocol: one of {@link #PROVIDER_OPENAI}, {@link #PROVIDER_LMSTUDIO}, {@link #PROVIDER_ANTHROPIC}. */
	public static final String P_PROVIDER = "provider"; //$NON-NLS-1$
	/** Host name or IP (optionally with scheme, e.g. http://192.168.1.36). */
	public static final String P_HOST = "host"; //$NON-NLS-1$
	/** Server port, e.g. 1234 for LM Studio. */
	public static final String P_PORT = "port"; //$NON-NLS-1$
	/** API key / token. Optional for local LM Studio. */
	public static final String P_API_KEY = "apiKey"; //$NON-NLS-1$
	/** Model identifier sent with chat requests. */
	public static final String P_MODEL = "model"; //$NON-NLS-1$
	/** Request timeout in seconds. */
	public static final String P_TIMEOUT = "timeoutSeconds"; //$NON-NLS-1$
	/** Max tokens for a completion; 0 lets the server decide (Anthropic falls back to 4096). */
	public static final String P_MAX_TOKENS = "maxTokens"; //$NON-NLS-1$
	/** Sampling temperature (ignored by providers that do not accept it). */
	public static final String P_TEMPERATURE = "temperature"; //$NON-NLS-1$
	/** How refactoring results are applied: preview or apply. */
	public static final String P_REFACTOR_APPLY_MODE = "refactorApplyMode"; //$NON-NLS-1$
	/** Anthropic auth mode: {@link #AUTH_API_KEY} or {@link #AUTH_CLAUDE_CODE}. */
	public static final String P_ANTHROPIC_AUTH = "anthropicAuth"; //$NON-NLS-1$
	/** Absolute path to the Maven executable; empty = auto-detect. */
	public static final String P_MAVEN_EXEC = "mavenExecutable"; //$NON-NLS-1$
	/** Absolute path to the Gradle executable; empty = auto-detect. */
	public static final String P_GRADLE_EXEC = "gradleExecutable"; //$NON-NLS-1$
	/** Last Maven goals chosen in the Compile dialog. */
	public static final String P_MAVEN_GOALS = "mavenGoals"; //$NON-NLS-1$
	/** Last Gradle tasks chosen in the Compile dialog. */
	public static final String P_GRADLE_TASKS = "gradleTasks"; //$NON-NLS-1$

	public static final String DEFAULT_MAVEN_GOALS = "clean test-compile"; //$NON-NLS-1$
	public static final String DEFAULT_GRADLE_TASKS = "clean compileJava compileTestJava"; //$NON-NLS-1$

	/** OpenAI-compatible endpoints: /v1/models, /v1/chat/completions (LM Studio exposes these too). */
	public static final String PROVIDER_OPENAI = "openai"; //$NON-NLS-1$
	/** LM Studio native REST API: /api/v1/models, /api/v1/chat. */
	public static final String PROVIDER_LMSTUDIO = "lmstudio"; //$NON-NLS-1$
	/** Anthropic / Claude compatible: /v1/models, /v1/messages with x-api-key + anthropic-version. */
	public static final String PROVIDER_ANTHROPIC = "anthropic"; //$NON-NLS-1$

	public static final String APPLY_MODE_PREVIEW = "preview"; //$NON-NLS-1$
	public static final String APPLY_MODE_DIRECT = "apply"; //$NON-NLS-1$

	/** Use the API key field (x-api-key header). */
	public static final String AUTH_API_KEY = "apikey"; //$NON-NLS-1$
	/** Use the local Claude Code login (OAuth bearer token from Keychain / ~/.claude). */
	public static final String AUTH_CLAUDE_CODE = "claudecode"; //$NON-NLS-1$
}
