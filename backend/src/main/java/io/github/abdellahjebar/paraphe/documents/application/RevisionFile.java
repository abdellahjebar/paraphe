package io.github.abdellahjebar.paraphe.documents.application;

import io.github.abdellahjebar.paraphe.documents.domain.Revision;
import java.io.IOException;
import java.io.InputStream;

/**
 * A revision with its content opened for reading. Use it in a try-with-resources block so the
 * stream is always closed.
 */
public record RevisionFile(Revision revision, InputStream content) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        content.close();
    }
}
