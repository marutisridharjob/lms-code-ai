package com.lmscode.ai.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Client for LM Studio's native REST API:
 *
 * <pre>
 * GET  {base}/api/v1/models
 * POST {base}/api/v1/chat
 * </pre>
 *
 * The native API has evolved between LM Studio releases, so the response
 * parsing is deliberately tolerant: it accepts the OpenAI-style
 * {@code choices[0].message.content} as well as flat {@code message.content} /
 * {@code content} shapes (string or array of text blocks).
 */
public class LmStudioNativeClient extends AbstractHttpAiClient {

	public LmStudioNativeClient(AiClientSettings settings) {
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

	@Override
	public List<String> listModels() throws AiClientException {
		JsonObject response = getJson("/api/v1/models"); //$NON-NLS-1$
		List<String> models = new ArrayList<>();
		JsonElement list = response.get("data"); //$NON-NLS-1$
		if (list == null || !list.isJsonArray()) {
			list = response.get("models"); //$NON-NLS-1$
		}
		if (list != null && list.isJsonArray()) {
			for (JsonElement item : list.getAsJsonArray()) {
				if (item.isJsonPrimitive()) {
					models.add(item.getAsString());
				} else if (item.isJsonObject()) {
					JsonObject obj = item.getAsJsonObject();
					String id = stringMember(obj, "id"); //$NON-NLS-1$
					if (id == null) {
						id = stringMember(obj, "key"); //$NON-NLS-1$
					}
					if (id == null) {
						id = stringMember(obj, "model"); //$NON-NLS-1$
					}
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
		if (settings.maxTokens() > 0) {
			body.addProperty("max_tokens", settings.maxTokens()); //$NON-NLS-1$
		}
		body.addProperty("stream", false); //$NON-NLS-1$

		JsonObject response = postJson("/api/v1/chat", body); //$NON-NLS-1$
		String content = extractContent(response);
		if (content != null) {
			return content;
		}
		throw new AiClientException("Unexpected chat response shape:\n" + abbreviate(response.toString(), 1000));
	}

	private static String extractContent(JsonObject response) {
		// OpenAI style: choices[0].message.content
		JsonElement choices = response.get("choices"); //$NON-NLS-1$
		if (choices != null && choices.isJsonArray() && !choices.getAsJsonArray().isEmpty()) {
			JsonElement first = choices.getAsJsonArray().get(0);
			if (first.isJsonObject()) {
				String content = contentOf(first.getAsJsonObject().get("message")); //$NON-NLS-1$
				if (content != null) {
					return content;
				}
			}
		}
		// Flat: message.content
		String content = contentOf(response.get("message")); //$NON-NLS-1$
		if (content != null) {
			return content;
		}
		// Flat: content (string or array of text blocks)
		return contentOf(response);
	}

	private static String contentOf(JsonElement container) {
		if (container == null || !container.isJsonObject()) {
			return null;
		}
		JsonElement content = container.getAsJsonObject().get("content"); //$NON-NLS-1$
		if (content == null) {
			return null;
		}
		if (content.isJsonPrimitive()) {
			return content.getAsString();
		}
		if (content.isJsonArray()) {
			StringBuilder sb = new StringBuilder();
			for (JsonElement block : content.getAsJsonArray()) {
				if (block.isJsonPrimitive()) {
					sb.append(block.getAsString());
				} else if (block.isJsonObject()) {
					JsonElement text = block.getAsJsonObject().get("text"); //$NON-NLS-1$
					if (text != null && text.isJsonPrimitive()) {
						sb.append(text.getAsString());
					}
				}
			}
			return sb.isEmpty() ? null : sb.toString();
		}
		return null;
	}

	private static JsonObject message(String role, String content) {
		JsonObject m = new JsonObject();
		m.addProperty("role", role); //$NON-NLS-1$
		m.addProperty("content", content); //$NON-NLS-1$
		return m;
	}
}
