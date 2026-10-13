package io.github.abdellahjebar.paraphe.submission.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.documents.application.DocumentService;
import io.github.abdellahjebar.paraphe.documents.domain.Document;
import io.github.abdellahjebar.paraphe.projects.application.ProjectService;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.projects.domain.ReviewerRole;
import io.github.abdellahjebar.paraphe.reviews.application.ReviewLinkService;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import io.github.abdellahjebar.paraphe.support.TestFixtures;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Proves the submission is all-or-nothing by making the last step fail after the file was stored,
 * the review opened and the first link issued.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SubmissionAtomicityIntegrationTest {

    private static final Path FILES = Path.of("target/test-files").toAbsolutePath();

    @Autowired
    private SubmissionService submission;

    @Autowired
    private DocumentService documents;

    @Autowired
    private ProjectService projects;

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private JdbcClient jdbc;

    @MockitoSpyBean
    private ReviewLinkService linkService;

    private static long storedFiles() throws IOException {
        if (!Files.exists(FILES)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(FILES)) {
            return walk.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".pdf")).count();
        }
    }

    @Test
    void aFailureOnTheLastStepLeavesNoRevisionNoReviewNoLinkAndNoFile() throws Exception {
        Project project = fixtures.project();
        ReviewerId first = fixtures.participant(project.id(), ReviewerRole.CONTROL_OFFICE).id();
        ReviewerId second = fixtures.participant(project.id(), ReviewerRole.ENGINEERING_OFFICE).id();
        Document document = documents.createDocument(project.id(), "Plan", null, null, "contractor");
        doThrow(new IllegalStateException("simulated failure")).when(linkService)
                .issue(any(), eq(second), any());
        doThrow(new IllegalStateException("simulated failure")).when(linkService)
                .issue(any(), eq(first), any());
        long filesBefore = storedFiles();

        assertThatThrownBy(() -> submission.submitForReview(new SubmitRequest(document.id(), "A",
                new ByteArrayInputStream(TestFixtures.PDF), "plan.pdf", null, Set.of(first, second), "contractor")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated failure");

        assertThat(documents.revisions(document.id())).isEmpty();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM review_request rr
                JOIN revision r ON r.id = rr.revision_id
                WHERE r.document_id = :d
                """).param("d", document.id().value()).query(Integer.class).single()).isZero();
        assertThat(storedFiles()).isEqualTo(filesBefore);
        assertThat(projects.participants(project.id())).hasSize(2);
    }
}
