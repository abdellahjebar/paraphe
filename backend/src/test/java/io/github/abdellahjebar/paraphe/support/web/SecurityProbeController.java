package io.github.abdellahjebar.paraphe.support.web;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/other")
    String other() {
        return "other";
    }

    /** An unexpected failure whose message must never reach the caller. */
    @GetMapping("/api/probe/boom")
    String boom() {
        throw new IllegalStateException("internal secret detail 4242");
    }
}
