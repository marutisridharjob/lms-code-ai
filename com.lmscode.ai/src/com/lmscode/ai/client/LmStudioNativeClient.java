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
 * The native chat endpoint takes a required {@code input} field (a union of a
 * plain string or an array of {@code {role, content}} messages). The message
 * array form is tried first; if the server rejects it the request is retried
 * once with the conversation flattened to a single string. Response parsing is
 * deliberately tolerant: OpenAI-style {@code choices[0].message.content},
 * flat {@code message.content} / {@code content}, Responses-style
 * {@code output[].content[].text} and {@code output_text} are all accepted.
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
		// Preferred shape: input as an array of {role, content} messages.
		JsonArray inputMessages = new JsonArray();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			inputMessages.add(message("system", systemPrompt)); //$NON-NLS-1$
		}
		for (ChatMessage m : messages) {
			inputMessages.add(message(m.role(), m.content()));
		}
		try {
			return send(body(inputMessages));
		} catch (AiClientException e) {
			String text = e.getMessage() == null ? "" : e.getMessage(); //$NON-NLS-1$
			if (!text.contains("input")) { //$NON-NLS-1$
				throw e;
			}
			// Fallback shape: input as one flattened string.
			StringBuilder flat = new StringBuilder();
			if (systemPrompt != null && !systemPrompt.isBlank()) {
				flat.append(systemPrompt).append("\n\n"); //$NON-NLS-1$
			}
			for (ChatMessage m : messages) {
				flat.append(m.role()).append(": ").append(m.content()).append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			JsonObject fallback = new JsonObject();
			copyModel(fallback);
			fallback.addProperty("input", flat.toString()); //$NON-NLS-1$
			fallback.addProperty("stream", false); //$NON-NLS-1$
			return send(fallback);
		}
	}

	private JsonObject body(JsonArray inputMessages) {
		JsonObject body = new JsonObject();
		copyModel(body);
		body.add("input", inputMessages); //$NON-NLS-1$
		body.addProperty("stream", false); //$NON-NLS-1$
		return body;
	}

	private void copyModel(JsonObject body) {
		if (settings.model() != null && !settings.model().isBlank()) {
			body.addProperty("model", settings.model()); //$NON-NLS-1$
		}
	}

	private String send(JsonObject body) throws AiClientException {
		JsonObject response = postJson("/api/v1/chat", body); //$NON-NLS-1$
		String content = extractContent(response);
		if (content != null && !content.isBlank()) {
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
		// Responses style convenience field: output_text
		String outputText = stringMember(response, "output_text"); //$NON-NLS-1$
		if (outputText != null && !outputText.isBlank()) {
			return outputText;
		}
		// Responses style: output[] items with content[] text blocks
		JsonElement output = response.get("output"); //$NON-NLS-1$
		if (output != null && output.isJsonArray()) {
			StringBuilder sb = new StringBuilder();
			for (JsonElement item : output.getAsJsonArray()) {
				if (item.isJsonObject()) {
					String text = contentOf(item);
					if (text != null) {
						sb.append(text);
					}
				}
			}
			if (!sb.isEmpty()) {
				return sb.toString();
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
