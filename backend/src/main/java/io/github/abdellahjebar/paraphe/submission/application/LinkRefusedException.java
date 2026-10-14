package io.github.abdellahjebar.paraphe.submission.application;

/**
 * A review link that cannot be used (unknown, expired or revoked). The reason is deliberately not
 * carried: the caller must not be able to tell which of the three it was.
 */
public class LinkRefusedException extends RuntimeException {

    public LinkRefusedException() {
        super("Review link refused");
    }
}
