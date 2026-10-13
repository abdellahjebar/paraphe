package io.github.abdellahjebar.paraphe.submission.application;

/** A revision cannot be sent to nobody: no reviewer was given and the work package has no default. */
public class NoReviewersException extends RuntimeException {

    public NoReviewersException() {
        super("No reviewer to send the revision to: give reviewers or set defaults for the work package");
    }
}
