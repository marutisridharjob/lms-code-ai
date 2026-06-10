package com.lmscode.ai.client;

import com.lmscode.ai.preferences.PreferenceConstants;

public final class AiClientFactory {

	private AiClientFactory() {
	}

	/** Client configured from the current workspace preferences. */
	public static AiClient fromPreferences() {
		return create(AiClientSettings.fromPreferences());
	}

	public static AiClient create(AiClientSettings settings) {
		return switch (settings.provider()) {
			case PreferenceConstants.PROVIDER_LMSTUDIO -> new LmStudioNativeClient(settings);
			case PreferenceConstants.PROVIDER_ANTHROPIC -> new AnthropicClient(settings);
			default -> new OpenAiCompatibleClient(settings);
		};
	}
}
