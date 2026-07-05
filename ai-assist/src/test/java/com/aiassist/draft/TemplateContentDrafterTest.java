package com.aiassist.draft;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateContentDrafterTest {

    private final TemplateContentDrafter drafter = new TemplateContentDrafter();

    private static final String NOTES = """
            The team reviewed the third-quarter roadmap and agreed the mobile release is the top priority.
            Performance testing showed a 40 percent improvement after the caching change.
            We need to finalize the security review before the end of the month.
            Sarah will follow up with the design team about the new onboarding flow.
            The budget for cloud infrastructure was approved.
            """;

    @Test
    void draftsDetailedDocumentWithSectionsKeyPointsAndActions() {
        Draft draft = drafter.draft("Q3 planning", NOTES, DraftOptions.defaults());

        assertThat(draft.title()).isEqualTo("Q3 planning");
        assertThat(draft.summary()).contains("Q3 planning");
        assertThat(draft.sections()).extracting(Draft.Section::heading)
                .contains("Overview", "Details");
        assertThat(draft.keyPoints()).isNotEmpty();
        assertThat(draft.actionItems())
                .anySatisfy(item -> assertThat(item).containsIgnoringCase("security review"))
                .anySatisfy(item -> assertThat(item).containsIgnoringCase("follow up"));
        assertThat(draft.fullText()).startsWith("# Q3 planning").contains("## Details");
        assertThat(draft.generatedBy()).isEqualTo("template");
    }

    @Test
    void meetingNotesAlwaysIncludeActionItemsSection() {
        Draft draft = drafter.draft("Sync", "We talked about the launch.",
                new DraftOptions(DraftOptions.ContentType.MEETING_NOTES, DraftOptions.Tone.CONCISE));

        assertThat(draft.sections()).extracting(Draft.Section::heading)
                .contains("Discussion", "Action items");
        assertThat(sectionBody(draft, "Action items")).contains("No action items were captured.");
    }

    @Test
    void emailDraftHasGreetingBodyAndClosing() {
        Draft draft = drafter.draft("Renewal reminder", NOTES,
                new DraftOptions(DraftOptions.ContentType.EMAIL, DraftOptions.Tone.FRIENDLY));

        assertThat(draft.sections()).extracting(Draft.Section::heading)
                .containsSubsequence("Greeting", "Body", "Closing");
        assertThat(sectionBody(draft, "Greeting")).isEqualTo("Hi there,");
    }

    @Test
    void usesFirstSentenceAsTitleWhenTopicIsUntitled() {
        Draft draft = drafter.draft("Untitled", "budget approved for next year. more detail here.",
                DraftOptions.defaults());

        assertThat(draft.title()).isEqualTo("Budget approved for next year");
    }

    private String sectionBody(Draft draft, String heading) {
        return draft.sections().stream()
                .filter(s -> s.heading().equals(heading))
                .map(Draft.Section::body)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing section " + heading
                        + " in " + List.copyOf(draft.sections())));
    }
}
