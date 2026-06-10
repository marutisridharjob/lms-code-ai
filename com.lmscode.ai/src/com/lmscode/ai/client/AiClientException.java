package com.lmscode.ai.client;

public class AiClientException extends Exception {

	private static final long serialVersionUID = 1L;

	public AiClientException(String message) {
		super(message);
	}

	public AiClientException(String message, Throwable cause) {
		super(message, cause);
	}
}
