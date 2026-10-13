package io.github.abdellahjebar.paraphe.support.web;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Endpoints that exist only in tests, standing in for the real API so the security rules can be
 * proven before that API is written.
 */
@RestController
public class SecurityProbeController {

    @GetMapping("/api/probe")
    String api() {
        return "ok";
    }

    @PostMapping("/api/probe/write")
    String write() {
        return "written";
    }

    @GetMapping("/api/probe/projects/{projectId}")
    @PreAuthorize("@projectAccessGuard.isMember(#projectId, authentication)")
    String project(@PathVariable UUID projectId) {
        return "member";
    }

    @GetMapping("/r/{token}")
    String reviewer(@PathVariable String token) {
        return "reviewer";
    }

    @GetMapping("/other")
    String other() {
        return "other";
    }

    /** What the real API does for a project the caller is not a member of: "not found". */
    @RestControllerAdvice(basePackageClasses = SecurityProbeController.class)
    static class ProbeAdvice {

        @ExceptionHandler(AccessDeniedException.class)
        ResponseEntity<ProblemDetail> denied() {
            ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
            detail.setTitle("Not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
        }
    }
}
