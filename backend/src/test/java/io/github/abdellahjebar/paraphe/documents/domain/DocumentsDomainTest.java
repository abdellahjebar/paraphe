package io.github.abdellahjebar.paraphe.documents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.reviews.domain.RevisionId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DocumentsDomainTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    private static final StoredFile FILE = new StoredFile("k.pdf", 10, "a".repeat(64));

    private Revision revision(int sequence, String label, String link) {
        return new Revision(RevisionId.random(), DocumentId.random(), sequence, label, NOW, FILE, "plan.pdf", link);
    }

    @Test
    void documentTitleIsTrimmedAndMustNotBeBlank() {
        Document document = new Document(DocumentId.random(), ProjectId.random(), null, "  Plan d'étaiement ", null, NOW);

        assertThat(document.title()).isEqualTo("Plan d'étaiement");
        assertThatThrownBy(() -> new Document(DocumentId.random(), ProjectId.random(), null, " ", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankWorkPackageAndTypeBecomeAbsent() {
        Document document = new Document(DocumentId.random(), ProjectId.random(), "  ", "Plan", " ", NOW);

        assertThat(document.workPackageCode()).isNull();
        assertThat(document.type()).isNull();
    }

    @Test
    void revisionLabelIsFreeTextButTrimmedAndNotBlank() {
        assertThat(revision(1, "  REV 1 ", null).label()).isEqualTo("REV 1");
        assertThatThrownBy(() -> revision(1, "  ", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revisionSequenceStartsAtOne() {
        assertThatThrownBy(() -> revision(0, "A", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void externalLinkMustBeHttpOrHttps() {
        assertThat(revision(1, "A", "https://drive.example.org/file/d/abc/view").externalLink())
                .isEqualTo("https://drive.example.org/file/d/abc/view");
        assertThat(revision(1, "A", "   ").externalLink()).isNull();
        assertThatThrownBy(() -> revision(1, "A", "javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> revision(1, "A", "file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> revision(1, "A", "not a url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storedFileRequiresContentAndAValidFingerprint() {
        assertThatThrownBy(() -> new StoredFile("k.pdf", 0, "a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoredFile("k.pdf", 1, "XYZ"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pdfSignatureIsRecognisedByContentNotByName() {
        assertThat(PdfSignature.isPdf("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(PdfSignature.isPdf("<html>".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(PdfSignature.isPdf(new byte[0])).isFalse();
        assertThat(PdfSignature.isPdf("%PDF".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(PdfSignature.isPdf(null)).isFalse();
    }
}
