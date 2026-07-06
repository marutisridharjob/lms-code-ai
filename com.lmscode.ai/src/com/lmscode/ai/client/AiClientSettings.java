package com.lmscode.ai.client;

import java.net.URI;

import org.eclipse.jface.preference.IPreferenceStore;

import com.lmscode.ai.Activator;
import com.lmscode.ai.preferences.PreferenceConstants;

/**
 * Immutable snapshot of the connection settings. The user configures three
 * things: the host (a full URL such as {@code https://mac-micro.local:1234},
 * or a bare host/IP), the API base URI (such as {@code /api/v1}; empty means
 * the provider default) and an optional API key.
 */
public record AiClientSettings(
		String provider,
		String host,
		int port,
		String basePath,
		String apiKey,
		String anthropicAuth,
		String model,
		int timeoutSeconds,
		int maxTokens,
		double temperature) {

	/** Reads the current values from the plugin preference store. */
	public static AiClientSettings fromPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		double temperature;
		try {
			temperature = Double.parseDouble(store.getString(PreferenceConstants.P_TEMPERATURE).trim());
		} catch (NumberFormatException e) {
			temperature = 0.2d;
		}
		return new AiClientSettings(
				store.getString(PreferenceConstants.P_PROVIDER),
				store.getString(PreferenceConstants.P_HOST),
				store.getInt(PreferenceConstants.P_PORT),
				store.getString(PreferenceConstants.P_BASE_PATH),
				store.getString(PreferenceConstants.P_API_KEY),
				store.getString(PreferenceConstants.P_ANTHROPIC_AUTH),
				store.getString(PreferenceConstants.P_MODEL),
				Math.max(1, store.getInt(PreferenceConstants.P_WAIT_MINUTES)) * 60,
				store.getInt(PreferenceConstants.P_MAX_TOKENS),
				temperature);
	}

	/**
	 * Base URL without trailing slash. The host accepts a bare name/IP
	 * ("192.168.1.36"), host:port ("192.168.1.36:1234"), or a full URL,
	 * optionally with a path prefix ("https://gateway:8443/lmstudio").
	 * The fallback port is applied only to scheme-less entries — a full URL
	 * typed by the user is respected exactly as given.
	 */
	public String baseUrl() {
		String h = host == null ? "" : host.trim(); //$NON-NLS-1$
		if (h.isEmpty()) {
			h = "localhost"; //$NON-NLS-1$
		}
		boolean hadScheme = h.regionMatches(true, 0, "http://", 0, 7) //$NON-NLS-1$
				|| h.regionMatches(true, 0, "https://", 0, 8); //$NON-NLS-1$
		if (!hadScheme) {
			h = "http://" + h; //$NON-NLS-1$
		}
		while (h.endsWith("/")) { //$NON-NLS-1$
			h = h.substring(0, h.length() - 1);
		}
		try {
			URI uri = URI.create(h);
			if (uri.getHost() != null) {
				int effectivePort = uri.getPort();
				if (effectivePort == -1 && !hadScheme && port > 0) {
					effectivePort = port; // legacy fallback for bare host entries
				}
				StringBuilder sb = new StringBuilder();
				sb.append(uri.getScheme()).append("://").append(uri.getHost()); //$NON-NLS-1$
				if (effectivePort > 0) {
					sb.append(':').append(effectivePort);
				}
				String path = uri.getRawPath();
				if (path != null && !path.isEmpty() && !"/".equals(path)) { //$NON-NLS-1$
					sb.append(path);
				}
				return sb.toString();
			}
		} catch (IllegalArgumentException e) {
			// unparsable — fall through to the string heuristic
		}
		if (!hadScheme && port > 0 && !h.matches(".*:\\d+(/.*)?$")) { //$NON-NLS-1$
			h = h + ":" + port; //$NON-NLS-1$
		}
		return h;
	}

	/**
	 * Endpoint path below {@link #baseUrl()}: the configured base URI (or the
	 * provider's default when empty) plus the endpoint suffix. For example
	 * with base URI {@code /api/v1} and endpoint {@code /chat} this returns
	 * {@code /api/v1/chat}.
	 */
	public String apiPath(String defaultBasePath, String endpoint) {
		String p = basePath == null ? "" : basePath.trim(); //$NON-NLS-1$
		if (p.isEmpty()) {
			p = defaultBasePath;
		}
		if (!p.startsWith("/")) { //$NON-NLS-1$
			p = "/" + p; //$NON-NLS-1$
		}
		while (p.endsWith("/")) { //$NON-NLS-1$
			p = p.substring(0, p.length() - 1);
		}
		return p + endpoint;
	}
}
