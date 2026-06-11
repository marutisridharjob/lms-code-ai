package com.lmscode.ai.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads the OAuth access token stored locally by Claude Code (the Anthropic
 * CLI the user logs into via Google/Anthropic account):
 *
 * <ul>
 * <li>{@code ~/.claude/.credentials.json} (Linux and some installs)</li>
 * <li>macOS Keychain, generic password {@code Claude Code-credentials}</li>
 * </ul>
 *
 * The JSON looks like {@code {"claudeAiOauth":{"accessToken":"sk-ant-oat01-…",
 * "expiresAt":1718000000000,…}}}. Tokens are short-lived; Claude Code
 * refreshes them whenever it runs.
 */
public final class ClaudeCodeCredentials {

	private ClaudeCodeCredentials() {
	}

	/** Returns a currently valid access token or throws with a actionable message. */
	public static String accessToken() throws AiClientException {
		String json = readCredentialsJson();
		try {
			JsonElement parsed = JsonParser.parseString(json);
			JsonObject oauth = parsed.getAsJsonObject().getAsJsonObject("claudeAiOauth"); //$NON-NLS-1$
			if (oauth == null) {
				throw new AiClientException(
						"Claude Code credentials found but no 'claudeAiOauth' entry. Re-login in Claude Code (run 'claude' and sign in).");
			}
			JsonElement tokenElement = oauth.get("accessToken"); //$NON-NLS-1$
			String token = tokenElement != null && tokenElement.isJsonPrimitive()
					? tokenElement.getAsString() : null;
			if (token == null || token.isBlank()) {
				throw new AiClientException(
						"Claude Code credentials contain no access token. Re-login in Claude Code (run 'claude').");
			}
			JsonElement expiresElement = oauth.get("expiresAt"); //$NON-NLS-1$
			if (expiresElement != null && expiresElement.isJsonPrimitive()) {
				try {
					long expiresAtMillis = (long) Double.parseDouble(expiresElement.getAsString());
					if (expiresAtMillis > 0 && Instant.ofEpochMilli(expiresAtMillis).isBefore(Instant.now())) {
						throw new AiClientException(
								"Your Claude Code login token expired. Open a terminal and run 'claude' once so it refreshes the token, then retry.");
					}
				} catch (NumberFormatException ignored) {
					// unknown expiry format — let the server decide
				}
			}
			return token;
		} catch (AiClientException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new AiClientException("Cannot parse Claude Code credentials: " + e.getMessage(), e);
		}
	}

	private static String readCredentialsJson() throws AiClientException {
		// 1. Plain credentials file
		File file = new File(System.getProperty("user.home", ""), ".claude/.credentials.json"); //$NON-NLS-1$ //$NON-NLS-2$
		if (file.isFile()) {
			try {
				return Files.readString(file.toPath(), StandardCharsets.UTF_8);
			} catch (Exception e) {
				throw new AiClientException("Cannot read " + file + ": " + e.getMessage(), e);
			}
		}
		// 2. macOS Keychain
		if (System.getProperty("os.name", "").toLowerCase().contains("mac")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String fromKeychain = readMacKeychain();
			if (fromKeychain != null) {
				return fromKeychain;
			}
		}
		throw new AiClientException(
				"No Claude Code credentials found (looked at ~/.claude/.credentials.json and the macOS Keychain entry"
						+ " 'Claude Code-credentials'). Install Claude Code and sign in with your account first,"
						+ " or switch Anthropic auth back to an API key in Preferences > LMS Code AI.");
	}

	private static String readMacKeychain() throws AiClientException {
		try {
			Process process = new ProcessBuilder(
					"security", "find-generic-password", "-s", "Claude Code-credentials", "-w") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
					.redirectErrorStream(false)
					.start();
			StringBuilder out = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					out.append(line);
				}
			}
			if (!process.waitFor(15, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new AiClientException(
						"Keychain lookup timed out. macOS may be waiting for you to allow Eclipse access to the"
								+ " 'Claude Code-credentials' Keychain item — click 'Allow' in the Keychain prompt and retry.");
			}
			if (process.exitValue() == 0 && !out.isEmpty()) {
				return out.toString().trim();
			}
			return null; // item not present
		} catch (AiClientException e) {
			throw e;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AiClientException("Keychain lookup interrupted.", e);
		} catch (Exception e) {
			throw new AiClientException("Keychain lookup failed: " + e.getMessage(), e);
		}
	}
}
