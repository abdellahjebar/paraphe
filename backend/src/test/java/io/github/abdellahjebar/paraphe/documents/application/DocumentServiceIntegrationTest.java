package io.github.abdellahjebar.paraphe.documents.application;

import io.github.abdellahjebar.paraphe.support.OwnerJdbc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.documents.domain.Document;
import io.github.abdellahjebar.paraphe.documents.domain.InvalidPdfException;
import io.github.abdellahjebar.paraphe.documents.domain.Revision;
import io.github.abdellahjebar.paraphe.projects.application.ProjectNotFoundException;
import io.github.abdellahjebar.paraphe.projects.application.ProjectService;
import io.github.abdellahjebar.paraphe.projects.application.WorkPackageNotFoundException;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.projects.domain.WorkPackage;
import io.github.abdellahjebar.paraphe.support.TestFixtures;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DocumentServiceIntegrationTest {

    private static final String ACTOR = "contractor";

    @Autowired
    private DocumentService service;

    @Autowired
    private ProjectService projects;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private AuditLog auditLog;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private OwnerJdbc owner;

    private Revision submit(Document document, String label, byte[] content, String fileName, String link) {
        return service.submitRevision(document.id(), label, new ByteArrayInputStream(content), fileName, link, ACTOR);
    }

    @Test
    void submittedFileIsStoredFingerprintedAndReadBack() throws Exception {
        Document document = fixtures.document(fixtures.project().id());

        Revision revision = submit(document, "A", TestFixtures.PDF, "plan.pdf", null);

        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(TestFixtures.PDF));
        assertThat(revision.file().sha256()).isEqualTo(expected);
        assertThat(revision.file().sizeBytes()).isEqualTo(TestFixtures.PDF.length);
        try (var file = service.open(revision.id())) {
            assertThat(file.content().readAllBytes()).isEqualTo(TestFixtures.PDF);
            assertThat(file.revision().label()).isEqualTo("A");
        }
    }

    @Test
    void revisionsGetIncreasingSequenceNumbers() {
        Document document = fixtures.document(fixtures.project().id());

        Revision first = submit(document, "A", TestFixtures.PDF, "a.pdf", null);
        Revision second = submit(document, "B", TestFixtures.PDF, "b.pdf", null);

        assertThat(first.sequence()).isEqualTo(1);
        assertThat(second.sequence()).isEqualTo(2);
        assertThat(service.revisions(document.id())).extracting(Revision::label).containsExactly("A", "B");
    }

    @Test
    void anOlderRevisionIsKeptWhenANewerOneIsSubmitted() throws Exception {
        Document document = fixtures.document(fixtures.project().id());
        Revision first = submit(document, "A", TestFixtures.PDF, "a.pdf", null);
        submit(document, "B", TestFixtures.PDF, "b.pdf", null);

        try (var file = service.open(first.id())) {
            assertThat(file.content().readAllBytes()).isEqualTo(TestFixtures.PDF);
        }
    }

    @Test
    void sameLabelTwiceIsRejectedEvenWithADifferentCase() {
        Document document = fixtures.document(fixtures.project().id());
        submit(document, "A", TestFixtures.PDF, "a.pdf", null);

        assertThatThrownBy(() -> submit(document, "a", TestFixtures.PDF, "a2.pdf", null))
                .isInstanceOf(DuplicateRevisionLabelException.class);
        assertThat(service.revisions(document.id())).hasSize(1);
    }

    @Test
    void sameLabelIsAllowedOnAnotherDocument() {
        Project project = fixtures.project();
        Document first = fixtures.document(project.id());
        Document second = fixtures.document(project.id());

        submit(first, "A", TestFixtures.PDF, "a.pdf", null);
        Revision other = submit(second, "A", TestFixtures.PDF, "a.pdf", null);

        assertThat(other.sequence()).isEqualTo(1);
    }

    @Test
    void contentThatIsNotAPdfIsRejectedWhateverTheFileName() {
        Document document = fixtures.document(fixtures.project().id());
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> submit(document, "A", html, "plan.pdf", null))
                .isInstanceOf(InvalidPdfException.class);
        assertThat(service.revisions(document.id())).isEmpty();
    }

    @Test
    void emptyContentIsRejected() {
        Document document = fixtures.document(fixtures.project().id());

        assertThatThrownBy(() -> submit(document, "A", new byte[0], "plan.pdf", null))
                .isInstanceOf(InvalidPdfException.class);
    }

    @Test
    void invalidLabelLeavesNoFileAndNoRevision() {
        Document document = fixtures.document(fixtures.project().id());

        assertThatThrownBy(() -> submit(document, "  ", TestFixtures.PDF, "plan.pdf", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(service.revisions(document.id())).isEmpty();
    }

    @Test
    void externalLinkIsStoredWhenItIsAnHttpUrl() {
        Document document = fixtures.document(fixtures.project().id());

        Revision revision = submit(document, "A", TestFixtures.PDF, "plan.pdf", "https://drive.example.org/f/1");

        assertThat(service.revision(revision.id()).externalLink()).isEqualTo("https://drive.example.org/f/1");
    }

    @Test
    void dangerousExternalLinkIsRejected() {
        Document document = fixtures.document(fixtures.project().id());

        assertThatThrownBy(() -> submit(document, "A", TestFixtures.PDF, "plan.pdf", "javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.revisions(document.id())).isEmpty();
    }

    @Test
    void fileNameIsSanitizedBeforeBeingStored() {
        Document document = fixtures.document(fixtures.project().id());

        Revision revision = submit(document, "A", TestFixtures.PDF, "../../etc/passwd.pdf", null);

        assertThat(service.revision(revision.id()).originalFileName()).isEqualTo("passwd.pdf");
    }

    @Test
    void documentCanBeFiledUnderAWorkPackageOfItsProject() {
        Project project = fixtures.project();
        WorkPackage lot = projects.addWorkPackage(project.id(), "Lot 01", "Gros œuvre", ACTOR);

        Document document = service.createDocument(project.id(), "Plan de poutres", lot.code(), "PLAN", ACTOR);

        assertThat(document.workPackageCode()).isEqualTo("Lot 01");
    }

    @Test
    void documentInAnUnknownWorkPackageIsRejected() {
        Project project = fixtures.project();

        assertThatThrownBy(() -> service.createDocument(project.id(), "Plan", "Lot 99", null, ACTOR))
                .isInstanceOf(WorkPackageNotFoundException.class);
    }

    @Test
    void documentCannotUseAWorkPackageOfAnotherProject() {
        Project mine = fixtures.project();
        Project other = fixtures.project();
        projects.addWorkPackage(other.id(), "Lot 77", "Autre", ACTOR);

        assertThatThrownBy(() -> service.createDocument(mine.id(), "Plan", "Lot 77", null, ACTOR))
                .isInstanceOf(WorkPackageNotFoundException.class);
    }

    @Test
    void documentOfAnUnknownProjectIsRejected() {
        assertThatThrownBy(() -> service.createDocument(ProjectId.random(), "Plan", null, null, ACTOR))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void revisionOfAnUnknownDocumentIsRejected() {
        assertThatThrownBy(() -> service.submitRevision(
                io.github.abdellahjebar.paraphe.documents.domain.DocumentId.random(), "A",
                new ByteArrayInputStream(TestFixtures.PDF), "plan.pdf", null, ACTOR))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void aSubmittedRevisionCannotBeChangedOrRemoved() {
        Document document = fixtures.document(fixtures.project().id());
        Revision revision = submit(document, "A", TestFixtures.PDF, "plan.pdf", null);

        assertThatThrownBy(() -> owner.client().sql("UPDATE revision SET label = 'Z' WHERE id = :id")
                .param("id", revision.id().value()).update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> owner.client().sql("DELETE FROM revision WHERE id = :id")
                .param("id", revision.id().value()).update())
                .hasMessageContaining("append-only");

        assertThat(service.revision(revision.id()).label()).isEqualTo("A");
    }

    @Test
    void creatingADocumentAndSubmittingARevisionLeaveAuditEvents() {
        Document document = fixtures.document(fixtures.project().id());
        Revision revision = submit(document, "A", TestFixtures.PDF, "plan.pdf", null);

        assertThat(auditLog.findByTarget("DOCUMENT", document.id().value()))
                .extracting(AuditEvent::type).containsExactly("DocumentCreated");
        List<AuditEvent> events = auditLog.findByTarget("REVISION", revision.id().value());
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("RevisionSubmitted");
            assertThat(event.actor()).isEqualTo(ACTOR);
            assertThat(event.payload()).contains(revision.file().sha256()).contains("\"label\"");
        });
    }

    @Test
    void auditPayloadDoesNotCarryTheFileName() {
        Document document = fixtures.document(fixtures.project().id());
        Revision revision = submit(document, "A", TestFixtures.PDF, "confidentiel-secret.pdf", null);

        assertThat(auditLog.findByTarget("REVISION", revision.id().value()).get(0).payload())
                .doesNotContain("confidentiel");
    }

    @Test
    void unknownRevisionIsReported() {
        assertThatThrownBy(() -> service.revision(
                io.github.abdellahjebar.paraphe.reviews.domain.RevisionId.random()))
                .isInstanceOf(RevisionNotFoundException.class);
    }
}
