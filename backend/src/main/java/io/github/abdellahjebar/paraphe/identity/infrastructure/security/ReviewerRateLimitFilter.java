package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits how often one address may call the public review endpoints. Without it, anyone can try
 * tokens at will and fill the audit journal with rejections.
 */
final class ReviewerRateLimitFilter extends OncePerRequestFilter {

    private final RequestRateLimiter limiter;

    ReviewerRateLimitFilter(RequestRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!limiter.tryAcquire("reviewer:" + request.getRemoteAddr())) {
            response.setHeader("Retry-After", String.valueOf(limiter.windowLength().toSeconds()));
            ProblemResponses.write(response, 429, "Too Many Requests", io.github.abdellahjebar.paraphe.shared.web.ErrorCode.TOO_MANY_REQUESTS);
            return;
        }
        chain.doFilter(request, response);
    }
}
