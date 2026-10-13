package io.github.abdellahjebar.paraphe.documents.domain;

/** The uploaded content is not a PDF. */
public class InvalidPdfException extends RuntimeException {

    public InvalidPdfException() {
        super("The uploaded file is not a PDF");
    }
}
