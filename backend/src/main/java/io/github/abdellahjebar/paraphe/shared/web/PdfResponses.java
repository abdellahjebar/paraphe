package io.github.abdellahjebar.paraphe.shared.web;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * How a stored PDF is sent back, for the contractor and for reviewers alike.
 *
 * <p>The type is fixed to PDF and sniffing is switched off, so a browser never reinterprets the
 * bytes. The stream is closed by the framework once it has been written.
 */
public final class PdfResponses {

    private PdfResponses() {
    }

    public static ResponseEntity<InputStreamResource> inline(InputStream content, String fileName, long sizeBytes) {
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(sizeBytes)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(content));
    }
}
