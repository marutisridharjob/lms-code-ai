package com.lmscode.ai.client;

import java.util.List;

/**
 * Provider-agnostic chat client. Implementations exist for OpenAI-compatible
 * servers (LM Studio's /v1 endpoints), LM Studio's native REST API (/api/v1)
 * and Anthropic / Claude compatible servers (/v1/messages).
 */
public interface AiClient {

	/** Lists model identifiers available on the server. */
	List<String> listModels() throws AiClientException;

	/**
	 * Sends a conversation and returns the assistant's reply text.
	 *
	 * @param systemPrompt optional system instructions, may be {@code null}
	 * @param messages     user/assistant turns, oldest first
	 */
	String chat(String systemPrompt, List<ChatMessage> messages) throws AiClientException;

	/** Human readable description, e.g. "qwen2.5-coder @ http://192.168.1.36:1234 (openai)". */
	String describe();
}
