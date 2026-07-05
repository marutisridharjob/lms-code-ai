package com.lmscode.ai.client;

import java.net.URI;

import org.eclipse.jface.preference.IPreferenceStore;

import com.lmscode.ai.Activator;
import com.lmscode.ai.preferences.PreferenceConstants;

/**
 * Immutable snapshot of the connection settings.
 */
public record AiClientSettings(
		String provider,
		String host,
		int port,
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
				store.getString(PreferenceConstants.P_API_KEY),
				store.getString(PreferenceConstants.P_ANTHROPIC_AUTH),
				store.getString(PreferenceConstants.P_MODEL),
				Math.max(1, store.getInt(PreferenceConstants.P_WAIT_MINUTES)) * 60,
				store.getInt(PreferenceConstants.P_MAX_TOKENS),
				temperature);
	}

	/**
	 * Base URL without trailing slash. The host preference accepts a bare
	 * name/IP ("192.168.1.36"), host:port ("192.168.1.36:1234"), or a full
	 * endpoint URL, optionally with a path prefix for reverse proxies
	 * ("https://my-gateway:8443/lmstudio"). The port preference is only used
	 * when the host does not already carry one, and is inserted before any
	 * path segment.
	 */
	public String baseUrl() {
		String h = host == null ? "" : host.trim(); //$NON-NLS-1$
		if (h.isEmpty()) {
			h = "localhost"; //$NON-NLS-1$
		}
		if (!h.regionMatches(true, 0, "http://", 0, 7) && !h.regionMatches(true, 0, "https://", 0, 8)) { //$NON-NLS-1$ //$NON-NLS-2$
			h = "http://" + h; //$NON-NLS-1$
		}
		while (h.endsWith("/")) { //$NON-NLS-1$
			h = h.substring(0, h.length() - 1);
		}
		try {
			URI uri = URI.create(h);
			if (uri.getHost() != null) {
				int effectivePort = uri.getPort() != -1 ? uri.getPort() : (port > 0 ? port : -1);
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
		if (port > 0 && !h.matches(".*:\\d+(/.*)?$")) { //$NON-NLS-1$
			h = h + ":" + port; //$NON-NLS-1$
		}
		return h;
	}
}
