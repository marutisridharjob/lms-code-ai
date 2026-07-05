package com.aiassist.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for live meeting transcription. The Vosk model is downloaded to
 * {@code modelDir} on first use unless a ready-to-use model already exists
 * there (or at an explicitly configured path).
 */
@ConfigurationProperties(prefix = "ai-assist.transcription")
public record TranscriptionProperties(String modelDir, String modelUrl, String modelName,
                                      float sampleRate, String preferredDevice) {

    public TranscriptionProperties {
        if (modelDir == null || modelDir.isBlank()) {
            modelDir = Path.of(System.getProperty("user.home"), ".ai-assist", "models").toString();
        }
        if (modelName == null || modelName.isBlank()) {
            modelName = "vosk-model-small-en-us-0.15";
        }
        if (modelUrl == null || modelUrl.isBlank()) {
            modelUrl = "https://alphacephei.com/vosk/models/" + modelName + ".zip";
        }
        if (sampleRate <= 0) {
            sampleRate = 16000f;
        }
    }
}
