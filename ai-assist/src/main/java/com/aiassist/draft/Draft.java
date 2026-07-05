package com.aiassist.draft;

import java.time.Instant;
import java.util.List;

/** A structured, detailed draft produced from a session's captured notes. */
public record Draft(String title,
                    String contentType,
                    String tone,
                    String summary,
                    List<Section> sections,
                    List<String> keyPoints,
                    List<String> actionItems,
                    String fullText,
                    String generatedBy,
                    Instant generatedAt) {

    public record Section(String heading, String body) {
    }
}
