import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.lmscode.ai.client.AiClient;
import com.lmscode.ai.client.AiClientException;
import com.lmscode.ai.client.AiClientFactory;
import com.lmscode.ai.client.AiClientSettings;
import com.lmscode.ai.client.ChatMessage;
import com.lmscode.ai.core.CodeExtractor;
import com.lmscode.ai.core.FindingsParser;
import com.lmscode.ai.core.FixFinding;

/**
 * Integration tests for the LMS Code client layer against a mock LM Studio
 * server implementing all three wire protocols (OpenAI-compatible, LM Studio
 * native REST, Anthropic Messages). Runs as plain Java — no OSGi needed:
 *
 *   bash tests/run-tests.sh
 *
 * Covers: endpoint URL configuration shapes (host / host:port / full URL /
 * reverse-proxy path prefix), request bodies and auth headers per protocol,
 * response parsing variants, the native "input" string fallback, timeout
 * ("model is slow") and error surfacing, and the findings/code parsers.
 */
public final class LmsCodeIntegrationTest {

	private static int passed;
	private static final List<String> failures = new ArrayList<>();

	/** Last request seen per path — lets tests assert on what the plugin sent. */
	private static final Map<String, String> lastBody = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, String>> lastHeaders = new ConcurrentHashMap<>();
	private static final AtomicBoolean nativeRejectArrayInput = new AtomicBoolean(false);

	public static void main(String[] args) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		int port = server.getAddress().getPort();
		mountAll(server, "");          // plain endpoints
		mountAll(server, "/gateway");  // reverse-proxy path prefix
		server.createContext("/slow/v1/chat/completions", ex -> {
			sleep(8000);
			respond(ex, 200, "{\"choices\":[{\"message\":{\"content\":\"too late\"}}]}");
		});
		server.createContext("/slow/v1/models", ex ->
				respond(ex, 200, "{\"data\":[{\"id\":\"slow-model\"}]}"));
		server.createContext("/broken/v1/chat/completions", ex ->
				respond(ex, 500, "{\"error\":\"boom\"}"));
		server.createContext("/garbage/v1/chat/completions", ex ->
				respond(ex, 200, "this is not json"));
		server.start();
		System.out.println("Mock LM Studio server on 127.0.0.1:" + port);

		try {
			openAiProtocol(port);
			lmStudioNativeProtocol(port);
			anthropicProtocol(port);
			endpointConfiguration(port);
			errorHandling(port);
			parsers();
		} finally {
			server.stop(0);
		}

		System.out.println();
		System.out.println("PASSED: " + passed + "   FAILED: " + failures.size());
		for (String failure : failures) {
			System.out.println("  FAIL: " + failure);
		}
		System.exit(failures.isEmpty() ? 0 : 1);
	}

	/* ===================== protocol: OpenAI-compatible ===================== */

	private static void openAiProtocol(int port) throws Exception {
		AiClient client = client("openai", "127.0.0.1", port, "secret-key", "coder-13b", 60);

		List<String> models = client.listModels();
		check("openai: listModels", models.equals(List.of("model-alpha", "model-beta")),
				"got " + models);

		String reply = client.chat("You are terse.", List.of(ChatMessage.user("hello there")));
		check("openai: chat reply", "openai-reply: hello there".equals(reply), "got " + reply);

		JsonObject sent = JsonParser.parseString(lastBody.get("/v1/chat/completions")).getAsJsonObject();
		JsonArray messages = sent.getAsJsonArray("messages");
		check("openai: model field", "coder-13b".equals(sent.get("model").getAsString()), sent.toString());
		check("openai: system first", "system".equals(messages.get(0).getAsJsonObject().get("role").getAsString())
				&& "You are terse.".equals(messages.get(0).getAsJsonObject().get("content").getAsString()),
				messages.toString());
		check("openai: stream false", !sent.get("stream").getAsBoolean(), sent.toString());
		check("openai: bearer auth", ("Bearer secret-key")
				.equals(lastHeaders.get("/v1/chat/completions").get("authorization")),
				String.valueOf(lastHeaders.get("/v1/chat/completions")));

		// No key -> no Authorization header
		AiClient anon = client("openai", "127.0.0.1", port, "", "coder-13b", 60);
		anon.listModels();
		check("openai: no auth header when key empty",
				lastHeaders.get("/v1/models").get("authorization") == null,
				String.valueOf(lastHeaders.get("/v1/models")));
	}

	/* ===================== protocol: LM Studio native ===================== */

	private static void lmStudioNativeProtocol(int port) throws Exception {
		AiClient client = client("lmstudio", "127.0.0.1", port, "", "qwen-coder", 60);

		List<String> models = client.listModels();
		check("native: listModels", models.equals(List.of("native-model")), "got " + models);

		String reply = client.chat("sys", List.of(ChatMessage.user("ping")));
		check("native: chat reply (Responses output[] shape)", "native-reply".equals(reply), "got " + reply);

		JsonObject sent = JsonParser.parseString(lastBody.get("/api/v1/chat")).getAsJsonObject();
		check("native: input is message array", sent.get("input").isJsonArray(), sent.toString());
		JsonArray input = sent.getAsJsonArray("input");
		check("native: system role in input", "system".equals(
				input.get(0).getAsJsonObject().get("role").getAsString()), input.toString());

		// Server rejects array input once -> client must fall back to string input
		nativeRejectArrayInput.set(true);
		try {
			String fallbackReply = client.chat("sys", List.of(ChatMessage.user("ping2")));
			check("native: string-input fallback", "native-reply".equals(fallbackReply), "got " + fallbackReply);
			JsonObject fallbackSent = JsonParser.parseString(lastBody.get("/api/v1/chat")).getAsJsonObject();
			check("native: fallback input is string", fallbackSent.get("input").isJsonPrimitive(),
					fallbackSent.toString());
		} finally {
			nativeRejectArrayInput.set(false);
		}
	}

	/* ===================== protocol: Anthropic / Claude ===================== */

	private static void anthropicProtocol(int port) throws Exception {
		AiClient client = client("anthropic", "127.0.0.1", port, "sk-ant-api-key", "claude-x", 60);

		List<String> models = client.listModels();
		check("anthropic: listModels", models.equals(List.of("model-alpha", "model-beta")), "got " + models);

		String reply = client.chat("be brief", List.of(ChatMessage.user("hi")));
		check("anthropic: chat reply (content[] text join)", "claude-reply".equals(reply), "got " + reply);

		JsonObject sent = JsonParser.parseString(lastBody.get("/v1/messages")).getAsJsonObject();
		check("anthropic: max_tokens default 4096", sent.get("max_tokens").getAsInt() == 4096, sent.toString());
		check("anthropic: system top-level", "be brief".equals(sent.get("system").getAsString()), sent.toString());
		Map<String, String> headers = lastHeaders.get("/v1/messages");
		check("anthropic: x-api-key header", "sk-ant-api-key".equals(headers.get("x-api-key")),
				String.valueOf(headers));
		check("anthropic: version header", "2023-06-01".equals(headers.get("anthropic-version")),
				String.valueOf(headers));

		// OAuth-format key -> bearer + oauth beta header, no x-api-key
		AiClient oauth = client("anthropic", "127.0.0.1", port, "sk-ant-oat01-token", "claude-x", 60);
		oauth.chat(null, List.of(ChatMessage.user("hi")));
		Map<String, String> oauthHeaders = lastHeaders.get("/v1/messages");
		check("anthropic: oauth bearer", "Bearer sk-ant-oat01-token".equals(oauthHeaders.get("authorization")),
				String.valueOf(oauthHeaders));
		check("anthropic: oauth beta header", "oauth-2025-04-20".equals(oauthHeaders.get("anthropic-beta")),
				String.valueOf(oauthHeaders));
		check("anthropic: no x-api-key with oauth", oauthHeaders.get("x-api-key") == null,
				String.valueOf(oauthHeaders));
	}

	/* ===================== endpoint URL configuration ===================== */

	private static void endpointConfiguration(int port) throws Exception {
		check("url: bare host + port", "http://127.0.0.1:" + port,
				settings("openai", "127.0.0.1", port).baseUrl());
		check("url: host:port in endpoint field wins over port pref", "http://127.0.0.1:" + port,
				settings("openai", "127.0.0.1:" + port, 9999).baseUrl());
		check("url: full URL", "http://127.0.0.1:" + port,
				settings("openai", "http://127.0.0.1:" + port, 9999).baseUrl());
		check("url: full URL + trailing slash", "http://127.0.0.1:" + port,
				settings("openai", "http://127.0.0.1:" + port + "/", 9999).baseUrl());
		check("url: path prefix preserved", "http://127.0.0.1:" + port + "/gateway",
				settings("openai", "http://127.0.0.1:" + port + "/gateway", 9999).baseUrl());
		check("url: full URL without port respected as-is", "http://gw/lms",
				settings("openai", "http://gw/lms", 1234).baseUrl());
		check("url: https kept", "https://secure.example:8443",
				settings("openai", "https://secure.example:8443", 1234).baseUrl());
		check("url: user's mac-micro example", "https://mac-micro.local:1234",
				settings("openai", "https://mac-micro.local:1234", 9999).baseUrl());
		check("url: empty host falls back to localhost", "http://localhost:1234",
				settings("openai", "", 1234).baseUrl());

		// The URI (API base path) field
		check("uri: empty uses provider default", "/v1/chat/completions",
				settings("openai", "h", 1, "").apiPath("/v1", "/chat/completions"));
		check("uri: /api/v1 overrides default", "/api/v1/chat/completions",
				settings("openai", "h", 1, "/api/v1").apiPath("/v1", "/chat/completions"));
		check("uri: normalized (no leading slash, trailing slash)", "/api/v1/chat",
				settings("lmstudio", "h", 1, "api/v1/").apiPath("/api/v1", "/chat"));

		// End-to-end: URI field routes an OpenAI-compatible client through /gateway/v1
		AiClient viaUri = AiClientFactory.create(
				settings("openai", "http://127.0.0.1:" + port, 0, "/gateway/v1"));
		String uriReply = viaUri.chat(null, List.of(ChatMessage.user("via uri")));
		check("uri: chat through configured base path", "openai-reply: via uri".equals(uriReply),
				"got " + uriReply);

		// End-to-end through a reverse-proxy style path prefix
		AiClient proxied = AiClientFactory.create(
				settings("openai", "http://127.0.0.1:" + port + "/gateway", 0));
		List<String> models = proxied.listModels();
		check("url: list models through path prefix", models.equals(List.of("model-alpha", "model-beta")),
				"got " + models);
		String reply = proxied.chat(null, List.of(ChatMessage.user("via proxy")));
		check("url: chat through path prefix", "openai-reply: via proxy".equals(reply), "got " + reply);
	}

	/* ===================== error handling ===================== */

	private static void errorHandling(int port) throws Exception {
		// Timeout -> "model is slow" message (6s budget vs 8s server sleep)
		AiClient slow = AiClientFactory.create(new AiClientSettings(
				"openai", "http://127.0.0.1:" + port + "/slow", 0, "", "", "apikey", "m", 6, 0, 0.2));
		try {
			slow.chat(null, List.of(ChatMessage.user("hi")));
			fail("timeout: expected AiClientException");
		} catch (AiClientException e) {
			check("timeout: slow-model message", e.getMessage().contains("The LMS model is slow"),
					e.getMessage());
		}

		AiClient broken = AiClientFactory.create(
				settings("openai", "http://127.0.0.1:" + port + "/broken", 0));
		try {
			broken.chat(null, List.of(ChatMessage.user("hi")));
			fail("http500: expected AiClientException");
		} catch (AiClientException e) {
			check("http500: status surfaced", e.getMessage().contains("HTTP 500"), e.getMessage());
		}

		AiClient garbage = AiClientFactory.create(
				settings("openai", "http://127.0.0.1:" + port + "/garbage", 0));
		try {
			garbage.chat(null, List.of(ChatMessage.user("hi")));
			fail("garbage: expected AiClientException");
		} catch (AiClientException e) {
			check("garbage: invalid JSON surfaced", e.getMessage().contains("Invalid JSON"), e.getMessage());
		}

		// Environments with a mandatory HTTP proxy resolve DNS at the proxy, so
		// UnknownHostException (and the .local hint) may not be reproducible —
		// assert only that a clear connection failure is surfaced.
		AiClient unknown = AiClientFactory.create(
				settings("openai", "definitely-not-a-real-host-xyz", 1234));
		try {
			unknown.listModels();
			fail("unknownhost: expected AiClientException");
		} catch (AiClientException e) {
			check("unknownhost: connection failure surfaced",
					e.getMessage().contains("Cannot reach") || e.getMessage().contains("Hint"),
					e.getMessage());
		}
	}

	/* ===================== parsers ===================== */

	private static void parsers() {
		List<FixFinding> plain = FindingsParser.parseFindings(
				"[{\"file\":\"/p/A.java\",\"line\":12,\"severity\":\"HIGH\",\"title\":\"NPE\","
						+ "\"description\":\"d\",\"fix\":\"f\"}]");
		check("parser: plain array", plain.size() == 1 && plain.get(0).line() == 12
				&& "/p/A.java".equals(plain.get(0).file()), String.valueOf(plain));

		List<FixFinding> fenced = FindingsParser.parseFindings(
				"Here are the issues:\n```json\n[{\"file\":\"B.java\",\"title\":\"bug\"}]\n```\nDone.");
		check("parser: fenced + prose", fenced.size() == 1 && "B.java".equals(fenced.get(0).file()),
				String.valueOf(fenced));

		List<FixFinding> wrapped = FindingsParser.parseFindings(
				"{\"findings\":[{\"path\":\"C.java\",\"lineNumber\":\"7\",\"message\":\"broken\","
						+ "\"recommendation\":\"fix it\"}]}");
		check("parser: wrapper object + aliases", wrapped.size() == 1
				&& "C.java".equals(wrapped.get(0).file()) && wrapped.get(0).line() == 7
				&& "broken".equals(wrapped.get(0).title()) && "fix it".equals(wrapped.get(0).fix()),
				String.valueOf(wrapped));

		List<FixFinding> single = FindingsParser.parseFindings(
				"{\"file\":\"D.java\",\"title\":\"one issue\"}");
		check("parser: single object wrapped", single.size() == 1 && "D.java".equals(single.get(0).file()),
				String.valueOf(single));

		check("parser: garbage -> empty", FindingsParser.parseFindings("no json here at all").isEmpty(), "");

		List<FindingsParser.FileChange> changes = FindingsParser.parseFileChanges(
				"[{\"file\":\"/p/E.java\",\"content\":\"class E {}\"}]");
		check("parser: file changes", changes.size() == 1 && "class E {}".equals(changes.get(0).content()),
				String.valueOf(changes));

		String code = CodeExtractor.extractCode("intro\n```java\nclass F {}\n```\ntail");
		check("extractor: fenced code", "class F {}\n".equals(code), "got [" + code + "]");
		check("extractor: no fence returns trimmed", "class G {}\n".equals(
				CodeExtractor.extractCode("  class G {}  ")), "");

		String jsonArray = CodeExtractor.extractJsonArray("noise [1,2,3] noise");
		check("extractor: json array span", "[1,2,3]".equals(jsonArray), "got " + jsonArray);
	}

	/* ===================== mock server ===================== */

	private static void mountAll(HttpServer server, String prefix) {
		server.createContext(prefix + "/v1/models", ex -> {
			record(ex, prefix + "/v1/models");
			respond(ex, 200, "{\"data\":[{\"id\":\"model-alpha\"},{\"id\":\"model-beta\"}]}");
		});

		server.createContext(prefix + "/v1/chat/completions", ex -> {
			String body = record(ex, prefix.isEmpty() ? "/v1/chat/completions" : prefix + "/v1/chat/completions");
			JsonObject request = JsonParser.parseString(body).getAsJsonObject();
			JsonArray messages = request.getAsJsonArray("messages");
			String lastUser = messages.get(messages.size() - 1).getAsJsonObject().get("content").getAsString();
			respond(ex, 200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"openai-reply: "
					+ lastUser + "\"}}]}");
		});

		server.createContext(prefix + "/api/v1/models", ex -> {
			record(ex, prefix + "/api/v1/models");
			respond(ex, 200, "{\"data\":[{\"id\":\"native-model\"}]}");
		});

		server.createContext(prefix + "/api/v1/chat", ex -> {
			String body = record(ex, prefix + "/api/v1/chat");
			JsonObject request = JsonParser.parseString(body).getAsJsonObject();
			JsonElement input = request.get("input");
			if (input == null) {
				respond(ex, 400, "{\"error\":{\"message\":\"'input' is required\",\"code\":\"invalid_union\"}}");
				return;
			}
			if (nativeRejectArrayInput.get() && input.isJsonArray()) {
				respond(ex, 400, "{\"error\":{\"message\":\"'input' must be a string\",\"param\":\"input\"}}");
				return;
			}
			respond(ex, 200, "{\"output\":[{\"content\":[{\"type\":\"text\",\"text\":\"native-reply\"}]}]}");
		});

		server.createContext(prefix + "/v1/messages", ex -> {
			record(ex, prefix + "/v1/messages");
			respond(ex, 200, "{\"content\":[{\"type\":\"text\",\"text\":\"claude-\"},"
					+ "{\"type\":\"text\",\"text\":\"reply\"}],\"stop_reason\":\"end_turn\"}");
		});
	}

	private static String record(HttpExchange exchange, String key) throws IOException {
		// The JDK HttpServer Title-Cases header names — store them lowercased
		// so assertions are transport-independent.
		Map<String, String> headers = new ConcurrentHashMap<>();
		exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
		lastHeaders.put(key, headers);
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		lastBody.put(key, body);
		return body;
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	/* ===================== helpers ===================== */

	private static AiClient client(String provider, String host, int port, String apiKey, String model,
			int timeoutSeconds) {
		return AiClientFactory.create(new AiClientSettings(
				provider, host, port, "", apiKey, "apikey", model, timeoutSeconds, 0, 0.2));
	}

	private static AiClientSettings settings(String provider, String host, int port) {
		return settings(provider, host, port, "");
	}

	private static AiClientSettings settings(String provider, String host, int port, String basePath) {
		return new AiClientSettings(provider, host, port, basePath, "", "apikey", "test-model", 60, 0, 0.2);
	}

	private static void check(String name, boolean condition, String detail) {
		if (condition) {
			passed++;
			System.out.println("  ok  " + name);
		} else {
			failures.add(name + " — " + detail);
			System.out.println("  FAIL " + name + " — " + detail);
		}
	}

	private static void check(String name, String expected, String actual) {
		check(name, expected.equals(actual), "expected " + expected + " but got " + actual);
	}

	private static void fail(String name) {
		failures.add(name);
		System.out.println("  FAIL " + name);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
