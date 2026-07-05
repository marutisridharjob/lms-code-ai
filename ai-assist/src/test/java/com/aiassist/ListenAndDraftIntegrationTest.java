package com.aiassist;

import java.util.Map;

import com.aiassist.draft.Draft;
import com.aiassist.listen.ListenController.SessionView;
import com.aiassist.listen.ListenController.TranscriptView;
import com.aiassist.listen.Utterance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // keep tests hands-off: no audio capture, no browser, no scheduling side effects
                "ai-assist.auto.start-capture=false",
                "ai-assist.auto.open-browser=false"
        })
class ListenAndDraftIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void fullListenThenDraftFlow() {
        ResponseEntity<SessionView> created = rest.postForEntity("/api/sessions",
                Map.of("topic", "Sprint retrospective"), SessionView.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = created.getBody().id();

        rest.postForEntity("/api/sessions/{id}/utterances",
                Map.of("text", "The release went out on time and customers reacted positively."),
                Utterance.class, id);
        ResponseEntity<Utterance> second = rest.postForEntity("/api/sessions/{id}/utterances",
                Map.of("text", "We need to improve our test coverage before the next sprint."),
                Utterance.class, id);
        assertThat(second.getBody().sequence()).isEqualTo(2);

        TranscriptView transcript = rest.getForObject("/api/sessions/{id}/transcript", TranscriptView.class, id);
        assertThat(transcript.transcript()).contains("release went out").contains("test coverage");

        ResponseEntity<Draft> drafted = rest.postForEntity("/api/sessions/{id}/draft",
                Map.of("contentType", "MEETING_NOTES", "tone", "PROFESSIONAL"), Draft.class, id);
        assertThat(drafted.getStatusCode()).isEqualTo(HttpStatus.OK);
        Draft draft = drafted.getBody();
        assertThat(draft.title()).isEqualTo("Sprint retrospective");
        assertThat(draft.contentType()).isEqualTo("MEETING_NOTES");
        assertThat(draft.actionItems()).anySatisfy(a -> assertThat(a).contains("test coverage"));
        assertThat(draft.fullText()).contains("# Sprint retrospective");
    }

    @Test
    void adHocDraftWithoutSession() {
        ResponseEntity<Draft> response = rest.postForEntity("/api/draft",
                Map.of("topic", "Launch email", "notes", "The launch is on Friday. Please join the call.",
                        "contentType", "EMAIL", "tone", "FRIENDLY"),
                Draft.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().sections()).isNotEmpty();
    }

    @Test
    void draftingAnEmptySessionIsRejected() {
        String id = rest.postForEntity("/api/sessions", Map.of(), SessionView.class).getBody().id();
        ResponseEntity<String> response = rest.postForEntity("/api/sessions/{id}/draft", Map.of(), String.class, id);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void unknownSessionReturns404() {
        ResponseEntity<String> response = rest.getForEntity("/api/sessions/does-not-exist", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void blankUtteranceIsRejected() {
        String id = rest.postForEntity("/api/sessions", Map.of(), SessionView.class).getBody().id();
        ResponseEntity<String> response = rest.postForEntity("/api/sessions/{id}/utterances",
                Map.of("text", "   "), String.class, id);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void liveStatusIsIdleWhenAutoStartDisabled() {
        ResponseEntity<Map> response = rest.getForEntity("/api/live/status", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("state")).isEqualTo("IDLE");
    }

    @Test
    void audioDeviceListingWorksEvenWithoutDevices() {
        ResponseEntity<Object[]> response = rest.getForEntity("/api/audio/devices", Object[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
