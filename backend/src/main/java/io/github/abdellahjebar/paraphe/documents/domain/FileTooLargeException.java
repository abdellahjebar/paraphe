package io.github.abdellahjebar.paraphe.documents.domain;

public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(long maxBytes) {
        super("The file is larger than the limit of %d bytes".formatted(maxBytes));
    }
}
