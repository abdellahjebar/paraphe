package io.github.abdellahjebar.paraphe.documents.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentFileNameTest {

    @Test
    void keepsOnlyTheLastPathSegment() {
        assertThat(DocumentService.sanitizeFileName("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(DocumentService.sanitizeFileName("C:\\Users\\x\\plan A.pdf")).isEqualTo("plan A.pdf");
    }

    @Test
    void dropsControlCharacters() {
        assertThat(DocumentService.sanitizeFileName("pl\u0000an\n.pdf")).isEqualTo("plan.pdf");
    }

    @Test
    void fallsBackToADefaultName() {
        assertThat(DocumentService.sanitizeFileName(null)).isEqualTo("document.pdf");
        assertThat(DocumentService.sanitizeFileName("   ")).isEqualTo("document.pdf");
        assertThat(DocumentService.sanitizeFileName("folder/")).isEqualTo("document.pdf");
    }

    @Test
    void cutsVeryLongNames() {
        assertThat(DocumentService.sanitizeFileName("x".repeat(400)).length()).isEqualTo(255);
    }
}
