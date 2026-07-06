package com.lmscode.ai.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Client for OpenAI-compatible servers, which includes LM Studio's
 * OpenAI-compatible surface:
 *
 * <pre>
 * GET  {base}/v1/models
 * POST {base}/v1/chat/completions
 * </pre>
 */
public class OpenAiCompatibleClient extends AbstractHttpAiClient {

	public OpenAiCompatibleClient(AiClientSettings settings) {
		super(settings);
	}

	@Override
	protected Map<String, String> headers() {
		String key = settings.apiKey();
		if (key == null || key.isBlank()) {
			return Map.of();
		}
		return Map.of("Authorization", "Bearer " + key); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/** Default API base URI when the preference is empty. */
	private static final String DEFAULT_BASE = "/v1"; //$NON-NLS-1$

	@Override
	public List<String> listModels() throws AiClientException {
		JsonObject response = getJson(settings.apiPath(DEFAULT_BASE, "/models")); //$NON-NLS-1$
		List<String> models = new ArrayList<>();
		JsonElement data = response.get("data"); //$NON-NLS-1$
		if (data != null && data.isJsonArray()) {
			for (JsonElement item : data.getAsJsonArray()) {
				if (item.isJsonObject()) {
					String id = stringMember(item.getAsJsonObject(), "id"); //$NON-NLS-1$
					if (id != null) {
						models.add(id);
					}
				}
			}
		}
		return models;
	}

	@Override
	public String chat(String systemPrompt, List<ChatMessage> messages) throws AiClientException {
		JsonObject body = new JsonObject();
		if (settings.model() != null && !settings.model().isBlank()) {
			body.addProperty("model", settings.model()); //$NON-NLS-1$
		}
		JsonArray wireMessages = new JsonArray();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			wireMessages.add(message("system", systemPrompt)); //$NON-NLS-1$
		}
		for (ChatMessage m : messages) {
			wireMessages.add(message(m.role(), m.content()));
		}
		body.add("messages", wireMessages); //$NON-NLS-1$
		body.addProperty("temperature", settings.temperature()); //$NON-NLS-1$
		if (settings.maxTokens() > 0) {
			body.addProperty("max_tokens", settings.maxTokens()); //$NON-NLS-1$
		}
		body.addProperty("stream", false); //$NON-NLS-1$

		JsonObject response = postJson(settings.apiPath(DEFAULT_BASE, "/chat/completions"), body); //$NON-NLS-1$
		JsonElement choices = response.get("choices"); //$NON-NLS-1$
		if (choices != null && choices.isJsonArray() && !choices.getAsJsonArray().isEmpty()) {
			JsonElement first = choices.getAsJsonArray().get(0);
			if (first.isJsonObject()) {
				JsonElement message = first.getAsJsonObject().get("message"); //$NON-NLS-1$
				if (message != null && message.isJsonObject()) {
					String content = stringMember(message.getAsJsonObject(), "content"); //$NON-NLS-1$
					if (content != null) {
						return content;
					}
				}
			}
		}
		throw new AiClientException("Unexpected chat response shape:\n" + abbreviate(response.toString(), 1000));
	}

	private static JsonObject message(String role, String content) {
		JsonObject m = new JsonObject();
		m.addProperty("role", role); //$NON-NLS-1$
		m.addProperty("content", content); //$NON-NLS-1$
		return m;
	}
}
