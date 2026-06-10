package com.lmscode.ai.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Shared HTTP/JSON plumbing for the provider clients, built on
 * {@link java.net.http.HttpClient} (no extra runtime dependencies).
 */
public abstract class AbstractHttpAiClient implements AiClient {

	protected final AiClientSettings settings;
	private final HttpClient http;

	protected AbstractHttpAiClient(AiClientSettings settings) {
		this.settings = settings;
		this.http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(Math.min(30, Math.max(5, settings.timeoutSeconds()))))
				.build();
	}

	/** Additional headers (auth etc.) added to every request. */
	protected abstract Map<String, String> headers();

	protected JsonObject getJson(String path) throws AiClientException {
		return execute(builder(path).GET().build());
	}

	protected JsonObject postJson(String path, JsonObject body) throws AiClientException {
		HttpRequest request = builder(path)
				.header("Content-Type", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
				.build();
		return execute(request);
	}

	private HttpRequest.Builder builder(String path) {
		HttpRequest.Builder b = HttpRequest.newBuilder()
				.uri(URI.create(settings.baseUrl() + path))
				.timeout(Duration.ofSeconds(Math.max(5, settings.timeoutSeconds())))
				.header("Accept", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
		headers().forEach(b::header);
		return b;
	}

	private JsonObject execute(HttpRequest request) throws AiClientException {
		HttpResponse<String> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new AiClientException("Cannot reach " + request.uri() + " (" + e.getMessage()
					+ "). Is the server running and the host/port correct?", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AiClientException("Request to " + request.uri() + " was interrupted.", e);
		}
		String bodyText = response.body() == null ? "" : response.body(); //$NON-NLS-1$
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new AiClientException("HTTP " + response.statusCode() + " from " + request.uri()
					+ (bodyText.isBlank() ? "" : ":\n" + abbreviate(bodyText, 2000)));
		}
		try {
			JsonElement parsed = JsonParser.parseString(bodyText);
			if (!parsed.isJsonObject()) {
				throw new AiClientException("Unexpected non-object JSON response from " + request.uri()
						+ ":\n" + abbreviate(bodyText, 500));
			}
			return parsed.getAsJsonObject();
		} catch (JsonSyntaxException e) {
			throw new AiClientException("Invalid JSON from " + request.uri() + ":\n" + abbreviate(bodyText, 500), e);
		}
	}

	protected static String abbreviate(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max) + "…"; //$NON-NLS-1$
	}

	/** Null-safe string member access. */
	protected static String stringMember(JsonObject obj, String name) {
		if (obj == null) {
			return null;
		}
		JsonElement e = obj.get(name);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	@Override
	public String describe() {
		String model = settings.model() == null || settings.model().isBlank()
				? "default model" : settings.model(); //$NON-NLS-1$
		return model + " @ " + settings.baseUrl() + " (" + settings.provider() + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
