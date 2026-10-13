package io.github.abdellahjebar.paraphe.support;

import io.github.abdellahjebar.paraphe.documents.application.DocumentService;
import io.github.abdellahjebar.paraphe.documents.domain.Document;
import io.github.abdellahjebar.paraphe.documents.domain.DocumentId;
import io.github.abdellahjebar.paraphe.documents.domain.Revision;
import io.github.abdellahjebar.paraphe.projects.application.ProjectService;
import io.github.abdellahjebar.paraphe.projects.domain.Participant;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import io.github.abdellahjebar.paraphe.projects.domain.ReviewerRole;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds real, valid projects, participants and revisions for integration tests, so tests run
 * against the same foreign keys and checks as production. All data is synthetic.
 */
@Component
public class TestFixtures {

    public static final String ACTOR = "test";

    /** The smallest content the application accepts as a PDF. */
    public static final byte[] PDF = "%PDF-1.4\n%synthetic test content\n".getBytes(StandardCharsets.US_ASCII);

    private final ProjectService projects;
    private final DocumentService documents;

    TestFixtures(ProjectService projects, DocumentService documents) {
        this.projects = projects;
        this.documents = documents;
    }

    public Project project() {
        return projects.createProject("Chantier test " + UUID.randomUUID(), ACTOR);
    }

    public Participant participant(ProjectId projectId, ReviewerRole role) {
        return projects.addParticipant(projectId, role.name() + " " + UUID.randomUUID(), role, null,
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.org", null, ACTOR);
    }

    public Document document(ProjectId projectId) {
        return documents.createDocument(projectId, "Plan " + UUID.randomUUID(), null, "PLAN", ACTOR);
    }

    /** A new document with a first revision labelled "A". */
    public Revision revision(ProjectId projectId) {
        return revisionOf(document(projectId).id(), "A");
    }

    public Revision revisionOf(DocumentId documentId, String label) {
        return documents.submitRevision(documentId, label, new ByteArrayInputStream(PDF), "plan.pdf", null, ACTOR);
    }
}
