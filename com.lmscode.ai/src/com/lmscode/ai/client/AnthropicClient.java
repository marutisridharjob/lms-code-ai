package com.lmscode.ai.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Client for Anthropic / Claude compatible servers:
 *
 * <pre>
 * GET  {base}/v1/models
 * POST {base}/v1/messages
 * </pre>
 *
 * Auth uses the {@code x-api-key} header plus {@code anthropic-version}.
 * The Messages API requires {@code max_tokens}; when the preference is 0
 * a default of 4096 is sent.
 */
public class AnthropicClient extends AbstractHttpAiClient {

	private static final String ANTHROPIC_VERSION = "2023-06-01"; //$NON-NLS-1$
	private static final int DEFAULT_MAX_TOKENS = 4096;

	public AnthropicClient(AiClientSettings settings) {
		super(settings);
	}

	@Override
	protected Map<String, String> headers() {
		String key = settings.apiKey();
		if (key == null || key.isBlank()) {
			return Map.of("anthropic-version", ANTHROPIC_VERSION); //$NON-NLS-1$
		}
		return Map.of(
				"x-api-key", key, //$NON-NLS-1$
				"anthropic-version", ANTHROPIC_VERSION); //$NON-NLS-1$
	}

	@Override
	public List<String> listModels() throws AiClientException {
		JsonObject response = getJson("/v1/models"); //$NON-NLS-1$
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
		body.addProperty("max_tokens", //$NON-NLS-1$
				settings.maxTokens() > 0 ? settings.maxTokens() : DEFAULT_MAX_TOKENS);
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			body.addProperty("system", systemPrompt); //$NON-NLS-1$
		}
		JsonArray wireMessages = new JsonArray();
		for (ChatMessage m : messages) {
			JsonObject wire = new JsonObject();
			wire.addProperty("role", m.role()); //$NON-NLS-1$
			wire.addProperty("content", m.content()); //$NON-NLS-1$
			wireMessages.add(wire);
		}
		body.add("messages", wireMessages); //$NON-NLS-1$

		JsonObject response = postJson("/v1/messages", body); //$NON-NLS-1$
		JsonElement content = response.get("content"); //$NON-NLS-1$
		if (content != null && content.isJsonArray()) {
			StringBuilder sb = new StringBuilder();
			for (JsonElement block : content.getAsJsonArray()) {
				if (block.isJsonObject()) {
					JsonObject obj = block.getAsJsonObject();
					if ("text".equals(stringMember(obj, "type"))) { //$NON-NLS-1$ //$NON-NLS-2$
						String text = stringMember(obj, "text"); //$NON-NLS-1$
						if (text != null) {
							sb.append(text);
						}
					}
				}
			}
			if (!sb.isEmpty()) {
				return sb.toString();
			}
		}
		throw new AiClientException("Unexpected messages response shape:\n" + abbreviate(response.toString(), 1000));
	}
}
