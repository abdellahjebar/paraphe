package io.github.abdellahjebar.paraphe.documents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.documents.domain.Document;
import io.github.abdellahjebar.paraphe.documents.domain.FileTooLargeException;
import io.github.abdellahjebar.paraphe.support.TestFixtures;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "paraphe.storage.max-file-size-bytes=64")
@Import(TestcontainersConfiguration.class)
class DocumentSizeLimitIntegrationTest {

    @Autowired
    private DocumentService service;

    @Autowired
    private TestFixtures fixtures;

    private static byte[] pdfOfSize(int size) {
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) 'x');
        byte[] header = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, content, 0, header.length);
        return content;
    }

    @Test
    void fileOverTheConfiguredLimitIsRejectedAndNothingIsRecorded() {
        Document document = fixtures.document(fixtures.project().id());

        assertThatThrownBy(() -> service.submitRevision(document.id(), "A",
                new ByteArrayInputStream(pdfOfSize(65)), "big.pdf", null, "contractor"))
                .isInstanceOf(FileTooLargeException.class);

        assertThat(service.revisions(document.id())).isEmpty();
    }

    @Test
    void fileExactlyAtTheLimitIsAccepted() {
        Document document = fixtures.document(fixtures.project().id());

        var revision = service.submitRevision(document.id(), "A",
                new ByteArrayInputStream(pdfOfSize(64)), "limit.pdf", null, "contractor");

        assertThat(revision.file().sizeBytes()).isEqualTo(64);
    }
}
