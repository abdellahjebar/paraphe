package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the small JSON error bodies the security filters produce before a controller is reached.
 * They follow the standard "problem details" shape and never echo the request path or its content.
 */
final class ProblemResponses {

    private ProblemResponses() {
    }

    static void write(HttpServletResponse response, int status, String title) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d}".formatted(title, status));
    }
}
