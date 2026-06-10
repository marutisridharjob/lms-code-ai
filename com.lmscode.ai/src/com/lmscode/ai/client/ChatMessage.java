package com.lmscode.ai.client;

/**
 * A single chat turn. Role is "user" or "assistant" ("system" is passed
 * separately so each provider can map it to its own wire format).
 */
public record ChatMessage(String role, String content) {

	public static ChatMessage user(String content) {
		return new ChatMessage("user", content); //$NON-NLS-1$
	}

	public static ChatMessage assistant(String content) {
		return new ChatMessage("assistant", content); //$NON-NLS-1$
	}
}
