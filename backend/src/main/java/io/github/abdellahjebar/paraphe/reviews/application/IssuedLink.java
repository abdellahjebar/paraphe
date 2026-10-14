package io.github.abdellahjebar.paraphe.reviews.application;

import io.github.abdellahjebar.paraphe.reviews.domain.ReviewLinkId;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewToken;
import java.time.Instant;

/**
 * A freshly issued link. The token is available here and only here: hand it to the contractor
 * once, it cannot be recovered afterwards.
 */
public record IssuedLink(ReviewLinkId id, ReviewToken token, Instant expiresAt) {

    /**
     * The page a reviewer opens in a browser. The page then calls the review API under
     * {@code /r/{token}}. Built from the token, so treat the result as a secret.
     */
    public String path() {
        return "/relecture/" + token.value();
    }

    @Override
    public String toString() {
        return "IssuedLink[id=%s, expiresAt=%s, token=****]".formatted(id.value(), expiresAt);
    }
}
