package com.aiassist.draft;

import com.aiassist.listen.ListeningSession;
import com.aiassist.listen.SessionStore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The "draft" half of the assistant: turn a listening session's transcript
 * (or ad-hoc notes) into a detailed structured draft.
 */
@RestController
@RequestMapping("/api")
public class DraftController {

    private final SessionStore store;
    private final ContentDrafter drafter;
    private final DraftFileWriter fileWriter;

    public DraftController(SessionStore store, ContentDrafter drafter, DraftFileWriter fileWriter) {
        this.store = store;
        this.drafter = drafter;
        this.fileWriter = fileWriter;
    }

    private Draft draftAndSave(String topic, String transcript, DraftOptions options) {
        Draft draft = drafter.draft(topic, transcript, options);
        java.nio.file.Path saved = fileWriter.save(draft);
        return saved == null ? draft : draft.withSavedTo(saved.toString());
    }

    public record SessionDraftRequest(DraftOptions.ContentType contentType, DraftOptions.Tone tone) {
    }

    public record AdHocDraftRequest(@Size(max = 200) String topic,
                                    @NotBlank @Size(max = 100_000) String notes,
                                    DraftOptions.ContentType contentType,
                                    DraftOptions.Tone tone) {
    }

    /** Draft from everything captured in a listening session. */
    @PostMapping("/sessions/{id}/draft")
    public Draft draftFromSession(@PathVariable String id,
                                  @Valid @RequestBody(required = false) SessionDraftRequest request) {
        ListeningSession session = store.get(id);
        String transcript = session.transcript();
        if (transcript.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Session " + id + " has no captured utterances to draft from");
        }
        DraftOptions options = request == null
                ? DraftOptions.defaults()
                : new DraftOptions(request.contentType(), request.tone());
        return draftAndSave(session.topic(), transcript, options);
    }

    /** Draft directly from supplied notes, without a listening session. */
    @PostMapping("/draft")
    public Draft draftAdHoc(@Valid @RequestBody AdHocDraftRequest request) {
        String topic = request.topic() == null || request.topic().isBlank() ? "Untitled" : request.topic().strip();
        return draftAndSave(topic, request.notes(), new DraftOptions(request.contentType(), request.tone()));
    }
}
