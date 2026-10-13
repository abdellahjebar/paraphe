package io.github.abdellahjebar.paraphe.documents.domain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Recognises a PDF by its first bytes, whatever the file is called or claims to be. */
public final class PdfSignature {

    private static final byte[] MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private PdfSignature() {
    }

    /** Number of leading bytes {@link #isPdf(byte[])} needs. */
    public static int headerLength() {
        return MAGIC.length;
    }

    public static boolean isPdf(byte[] header) {
        return header != null
                && header.length >= MAGIC.length
                && Arrays.equals(Arrays.copyOf(header, MAGIC.length), MAGIC);
    }
}
