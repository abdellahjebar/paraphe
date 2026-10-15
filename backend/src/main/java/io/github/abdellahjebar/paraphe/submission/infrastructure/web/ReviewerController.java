package io.github.abdellahjebar.paraphe.submission.infrastructure.web;

import io.github.abdellahjebar.paraphe.documents.application.RevisionFile;
import io.github.abdellahjebar.paraphe.reviews.domain.Opinion;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import io.github.abdellahjebar.paraphe.shared.web.PdfResponses;
import io.github.abdellahjebar.paraphe.submission.application.LinkRefusedException;
import io.github.abdellahjebar.paraphe.submission.application.OpinionRecorded;
import io.github.abdellahjebar.paraphe.submission.application.ReviewerView;
import io.github.abdellahjebar.paraphe.submission.application.SubmissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a reviewer can do with their personal link, and nothing more.
 *
 * <p>An unusable link (unknown, expired, revoked) always ends in the same "not found", whatever the
 * reason. The reviewer is never shown the global status of the revision, the other reviewers, or
 * anything about the project beyond the document in front of them.
 */
@RestController
@RequestMapping("/r/{token}")
class ReviewerController {

    private final SubmissionService submission;

    ReviewerController(SubmissionService submission) {
        this.submission = submission;
    }

    record OpinionRequest(@NotBlank @Size(max = 20) String statusCode, @Size(max = 5000) String comment) {
    }

    record StatusOption(String code, String label, OpinionEffect effect) {
    }

    record MyOpinion(String statusCode, String statusLabel, String comment, Instant givenAt, boolean enteredByOther) {
        static MyOpinion of(Opinion o) {
            return new MyOpinion(o.status().code(), o.status().label(), o.comment(), o.givenAt(), o.isOnBehalf());
        }
    }

    record ReviewerPage(String documentTitle, String revisionLabel, int revisionSequence,
                        boolean revisionSuperseded, Instant linkExpiresAt, List<StatusOption> availableStatuses,
                        MyOpinion myLastOpinion) {
    }

    record OpinionAccepted(boolean recorded, boolean revisionSuperseded) {
    }

    @GetMapping
    ReviewerPage page(@PathVariable String token) {
        ReviewerView view = submission.openLink(token).orElseThrow(LinkRefusedException::new);
        return new ReviewerPage(view.documentTitle(), view.revisionLabel(), view.revisionSequence(),
                view.revisionSuperseded(), view.linkExpiresAt(),
                view.availableStatuses().stream()
                        .map(s -> new StatusOption(s.code(), s.label(), s.effect())).toList(),
                view.myLastOpinion().map(MyOpinion::of).orElse(null));
    }

    /**
     * Called by the page after the first real gesture. Until then the link stays "issued", so a scanner
     * that merely loads the page cannot make it look read.
     */
    @PostMapping("/seen")
    ResponseEntity<Void> seen(@PathVariable String token) {
        submission.confirmLinkSeen(token).orElseThrow(LinkRefusedException::new);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/document")
    ResponseEntity<InputStreamResource> document(@PathVariable String token) {
        RevisionFile file = submission.viewDocumentByLink(token).orElseThrow(LinkRefusedException::new);
        return PdfResponses.inline(file.content(), file.revision().originalFileName(),
                file.revision().file().sizeBytes());
    }

    @PostMapping("/opinion")
    OpinionAccepted opinion(@PathVariable String token, @Valid @RequestBody OpinionRequest body) {
        OpinionRecorded recorded = submission.giveOpinionByLink(token, body.statusCode(), body.comment())
                .orElseThrow(LinkRefusedException::new);
        // The global status is deliberately not returned: it describes the other reviewers too.
        return new OpinionAccepted(true, recorded.revisionSuperseded());
    }
}
