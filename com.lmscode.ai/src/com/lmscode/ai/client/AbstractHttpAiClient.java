package com.lmscode.ai.client;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Shared HTTP/JSON plumbing for the provider clients, built on
 * {@link java.net.http.HttpClient} (no extra runtime dependencies).
 *
 * <p>Hostname convenience: bare machine names like {@code mac-micro} are
 * usually only resolvable on a LAN via Bonjour/mDNS as
 * {@code mac-micro.local}. When such a name fails with an unknown-host
 * error, the request is retried once with {@code .local} appended and the
 * working form is remembered for subsequent requests.</p>
 */
public abstract class AbstractHttpAiClient implements AiClient {

	protected final AiClientSettings settings;
	private final HttpClient http;

	/** Host that actually resolved (e.g. "mac-micro.local"), once discovered. */
	private volatile String workingHost;

	protected AbstractHttpAiClient(AiClientSettings settings) {
		this.settings = settings;
		this.http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(Math.min(30, Math.max(5, settings.timeoutSeconds()))))
				.build();
	}

	/** Additional headers (auth etc.) added to every request. */
	protected abstract Map<String, String> headers() throws AiClientException;

	protected JsonObject getJson(String path) throws AiClientException {
		return request(path, HttpRequest.Builder::GET);
	}

	protected JsonObject postJson(String path, JsonObject body) throws AiClientException {
		return request(path, b -> b
				.header("Content-Type", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)));
	}

	private JsonObject request(String path, UnaryOperator<HttpRequest.Builder> customizer)
			throws AiClientException {
		String base = effectiveBaseUrl();
		try {
			return execute(build(base, path, customizer));
		} catch (AiClientException e) {
			String fallbackHost = mdnsFallbackHost(base);
			if (fallbackHost == null || !causedByUnknownHost(e)) {
				throw e;
			}
			String fallbackBase = replaceHost(base, fallbackHost);
			JsonObject result = execute(build(fallbackBase, path, customizer));
			workingHost = fallbackHost; // remember for subsequent requests
			return result;
		}
	}

	private String effectiveBaseUrl() {
		String base = settings.baseUrl();
		String host = workingHost;
		return host != null ? replaceHost(base, host) : base;
	}

	/** {@code mac-micro} → {@code mac-micro.local}; null when not applicable. */
	private static String mdnsFallbackHost(String baseUrl) {
		try {
			String host = URI.create(baseUrl).getHost();
			if (host != null && !host.contains(".") && !host.equalsIgnoreCase("localhost")) { //$NON-NLS-1$ //$NON-NLS-2$
				return host + ".local"; //$NON-NLS-1$
			}
		} catch (IllegalArgumentException e) {
			// unparsable base URL — no fallback
		}
		return null;
	}

	private static String replaceHost(String baseUrl, String newHost) {
		URI uri = URI.create(baseUrl);
		StringBuilder sb = new StringBuilder();
		sb.append(uri.getScheme()).append("://").append(newHost); //$NON-NLS-1$
		if (uri.getPort() != -1) {
			sb.append(':').append(uri.getPort());
		}
		return sb.toString();
	}

	private static boolean causedByUnknownHost(Throwable t) {
		for (Throwable cause = t; cause != null; cause = cause.getCause()) {
			if (cause instanceof UnknownHostException) {
				return true;
			}
		}
		return false;
	}

	private HttpRequest build(String baseUrl, String path, UnaryOperator<HttpRequest.Builder> customizer)
			throws AiClientException {
		HttpRequest.Builder b = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + path))
				.timeout(Duration.ofSeconds(Math.max(5, settings.timeoutSeconds())))
				.header("Accept", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$
		headers().forEach(b::header);
		return customizer.apply(b).build();
	}

	private JsonObject execute(HttpRequest request) throws AiClientException {
		HttpResponse<String> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (IOException e) {
			String message = "Cannot reach " + request.uri() + " (" + e.getMessage()
					+ "). Is the server running and the host/port correct?";
			if (causedByUnknownHost(e)) {
				message += "\nHint: Macs and many LAN machines resolve via Bonjour/mDNS as '<name>.local'"
						+ " (e.g. mac-micro.local). Try that, or use the IP address.";
			}
			throw new AiClientException(message, e);
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
		return model + " @ " + effectiveBaseUrl() + " (" + settings.provider() + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
