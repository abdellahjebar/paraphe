package io.github.abdellahjebar.paraphe.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abdellahjebar.paraphe.support.ApiScenario;
import io.github.abdellahjebar.paraphe.support.ApiTestBase;
import io.github.abdellahjebar.paraphe.support.TestFixtures;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class RevisionApiIntegrationTest extends ApiTestBase {

    private MockHttpSession session;
    private ApiScenario scenario;

    private void start() throws Exception {
        session = signIn(newUser());
        scenario = new ApiScenario(mvc, session).withProject();
    }

    // ---- documents ---------------------------------------------------------------------------

    @Test
    void documentCanBeCreatedListedAndRead() throws Exception {
        start();
        UUID document = scenario.document("Plan de poutres", "Lot 01");

        mvc.perform(get("/api/projects/{p}/documents", scenario.project).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Plan de poutres"))
                .andExpect(jsonPath("$[0].workPackage").value("Lot 01"))
                .andExpect(jsonPath("$[0].revisionCount").value(0));
        mvc.perform(get("/api/projects/{p}/documents/{d}", scenario.project, document).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisions").isEmpty());
    }

    @Test
    void documentInAnUnknownWorkPackageIsNotFound() throws Exception {
        start();

        mvc.perform(post("/api/projects/{p}/documents", scenario.project).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Plan\",\"workPackage\":\"Lot 99\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aDocumentOfAnotherProjectIsNotFoundWhateverItsIdentifier() throws Exception {
        start();
        MockHttpSession otherSession = signIn(newUser());
        ApiScenario other = new ApiScenario(mvc, otherSession).withProject();
        UUID foreignDocument = other.document("Document d'ailleurs", "Lot 01");

        MvcResult mixed = mvc.perform(get("/api/projects/{p}/documents/{d}", scenario.project, foreignDocument)
                .session(session)).andExpect(status().isNotFound()).andReturn();
        MvcResult missing = mvc.perform(get("/api/projects/{p}/documents/{d}", scenario.project, UUID.randomUUID())
                .session(session)).andExpect(status().isNotFound()).andReturn();

        assertThat(mixed.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
    }

    // ---- sending a revision ------------------------------------------------------------------

    @Test
    void depositingARevisionOpensTheReviewAndReturnsOneLinkPerReviewer() throws Exception {
        start();
        UUID document = scenario.document("Plan de poutres", "Lot 01");

        MvcResult result = scenario.submit(document, "A", null);

        mvc.perform(get("/api/projects/{p}/documents/{d}", scenario.project, document).session(session))
                .andExpect(jsonPath("$.revisions", hasSize(1)))
                .andExpect(jsonPath("$.revisions[0].label").value("A"))
                .andExpect(jsonPath("$.revisions[0].sha256").value(org.hamcrest.Matchers.hasLength(64)));
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains(scenario.control.toString()).contains(scenario.structure.toString());
        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(body, "$.links[*].url"))
                .hasSize(2)
                .allSatisfy(url -> assertThat(url).startsWith("http://localhost:8080/relecture/"));
    }

    @Test
    void explicitReviewersOverrideTheDefaults() throws Exception {
        start();
        UUID document = scenario.document("Plan", "Lot 01");

        MvcResult result = scenario.submit(document, "A", List.of(scenario.architect));

        assertThat(com.jayway.jsonpath.JsonPath.<List<String>>read(result.getResponse().getContentAsString(),
                "$.links[*].reviewerId")).containsExactly(scenario.architect.toString());
    }

    @Test
    void sendingToNobodyIsUnprocessable() throws Exception {
        start();
        UUID document = scenario.document("Sans lot", null);

        mvc.perform(multipart("/api/projects/{p}/documents/{d}/revisions", scenario.project, document)
                        .file(new MockMultipartFile("file", "plan.pdf", "application/pdf", TestFixtures.PDF))
                        .param("label", "A").with(csrf()).session(session))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aReviewerFromAnotherProjectIsUnprocessable() throws Exception {
        start();
        UUID document = scenario.document("Plan", "Lot 01");
        ApiScenario other = new ApiScenario(mvc, signIn(newUser())).withProject();

        mvc.perform(multipart("/api/projects/{p}/documents/{d}/revisions", scenario.project, document)
                        .file(new MockMultipartFile("file", "plan.pdf", "application/pdf", TestFixtures.PDF))
                        .param("label", "A").param("reviewerIds", other.control.toString())
                        .with(csrf()).session(session))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aFileThatIsNotAPdfIsRefusedWhateverItIsCalled() throws Exception {
        start();
        UUID document = scenario.document("Plan", "Lot 01");

        mvc.perform(multipart("/api/projects/{p}/documents/{d}/revisions", scenario.project, document)
                        .file(new MockMultipartFile("file", "plan.pdf", "application/pdf",
                                "<html>not a pdf</html>".getBytes(StandardCharsets.UTF_8)))
                        .param("label", "A").with(csrf()).session(session))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/projects/{p}/documents/{d}", scenario.project, document).session(session))
                .andExpect(jsonPath("$.revisions").isEmpty());
    }

    @Test
    void theSameIndexTwiceIsAConflict() throws Exception {
        start();
        UUID document = scenario.document("Plan", "Lot 01");
        scenario.submit(document, "A", null);

        mvc.perform(multipart("/api/projects/{p}/documents/{d}/revisions", scenario.project, document)
                        .file(new MockMultipartFile("file", "plan.pdf", "application/pdf", TestFixtures.PDF))
                        .param("label", "a").with(csrf()).session(session))
                .andExpect(status().isConflict());
    }

    @Test
    void depositNeedsAFileAndAnIndex() throws Exception {
        start();
        UUID document = scenario.document("Plan", "Lot 01");

        mvc.perform(multipart("/api/projects/{p}/documents/{d}/revisions", scenario.project, document)
                        .param("label", "A").with(csrf()).session(session))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/projects/{p}/documents/{d}/revisions", scenario.project, document)
                        .file(new MockMultipartFile("file", "plan.pdf", "application/pdf", TestFixtures.PDF))
                        .with(csrf()).session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theContractorCanDownloadTheExactFileThatWasDeposited() throws Exception {
        start();
        UUID document = scenario.document("Plan", "Lot 01");
        UUID revision = ApiScenario.revisionId(scenario.submit(document, "A", null));

        MvcResult result = mvc.perform(get("/api/projects/{p}/revisions/{r}/file", scenario.project, revision)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", startsWith("inline")))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(TestFixtures.PDF);
    }

    @Test
    void aRevisionOfAnotherProjectCannotBeDownloaded() throws Exception {
        start();
        ApiScenario other = new ApiScenario(mvc, signIn(newUser())).withProject();
        UUID foreignRevision = ApiScenario.revisionId(other.submit(other.document("Plan", "Lot 01"), "A", null));

        mvc.perform(get("/api/projects/{p}/revisions/{r}/file", scenario.project, foreignRevision).session(session))
                .andExpect(status().isNotFound());
    }

    // ---- the review --------------------------------------------------------------------------

    @Test
    void reviewShowsWhoIsExpectedAndWhoHasNotAnswered() throws Exception {
        start();
        UUID revision = ApiScenario.revisionId(scenario.submit(scenario.document("Plan", "Lot 01"), "A", null));

        mvc.perform(get("/api/projects/{p}/revisions/{r}/review", scenario.project, revision).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.superseded").value(false))
                .andExpect(jsonPath("$.reviewers", hasSize(2)))
                .andExpect(jsonPath("$.reviewers[*].answered").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(false))))
                .andExpect(jsonPath("$.reviewers[0].linkStatus").value("ISSUED"));
    }

    @Test
    void issuingANewLinkReplacesTheOldOne() throws Exception {
        start();
        UUID document = scenario.document("Plan", "Lot 01");
        MvcResult submitted = scenario.submit(document, "A", null);
        UUID revision = ApiScenario.revisionId(submitted);
        String oldToken = ApiScenario.tokenOf(submitted, scenario.control);

        MvcResult fresh = mvc.perform(post("/api/projects/{p}/revisions/{r}/reviewers/{x}/link",
                        scenario.project, revision, scenario.control).with(csrf()).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(containsString("/relecture/")))
                .andReturn();
        String newToken = read(fresh, "$.url");

        mvc.perform(get("/r/{t}", oldToken)).andExpect(status().isNotFound());
        mvc.perform(get("/r/{t}", newToken.substring(newToken.lastIndexOf('/') + 1))).andExpect(status().isOk());
    }

    @Test
    void revokingALinkMakesItUnusable() throws Exception {
        start();
        MvcResult submitted = scenario.submit(scenario.document("Plan", "Lot 01"), "A", null);
        UUID revision = ApiScenario.revisionId(submitted);
        String token = ApiScenario.tokenOf(submitted, scenario.control);

        mvc.perform(post("/api/projects/{p}/revisions/{r}/reviewers/{x}/link/revoke",
                        scenario.project, revision, scenario.control).with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/r/{t}", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/{p}/revisions/{r}/review", scenario.project, revision).session(session))
                .andExpect(jsonPath("$.reviewers[?(@.reviewerId=='%s')].linkStatus".formatted(scenario.control))
                        .value("REVOKED"));
    }

    @Test
    void linkForAReviewerWhoIsNotExpectedIsUnprocessable() throws Exception {
        start();
        UUID revision = ApiScenario.revisionId(scenario.submit(scenario.document("Plan", "Lot 01"), "A", null));

        mvc.perform(post("/api/projects/{p}/revisions/{r}/reviewers/{x}/link",
                        scenario.project, revision, scenario.architect).with(csrf()).session(session))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void contractorCanEnterAnAnswerOnBehalfAndItIsMarkedAsSuch() throws Exception {
        start();
        UUID revision = ApiScenario.revisionId(scenario.submit(scenario.document("Plan", "Lot 01"), "A", null));

        mvc.perform(post("/api/projects/{p}/revisions/{r}/opinions", scenario.project, revision)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerId\":\"%s\",\"statusCode\":\"VAO\",\"comment\":\"par téléphone\"}"
                                .formatted(scenario.control)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(get("/api/projects/{p}/revisions/{r}/review", scenario.project, revision).session(session))
                .andExpect(jsonPath("$.reviewers[?(@.reviewerId=='%s')].lastOpinion.enteredBy"
                        .formatted(scenario.control)).value(org.hamcrest.Matchers.hasItem(startsWith("user:"))))
                .andExpect(jsonPath("$.reviewers[?(@.reviewerId=='%s')].answered".formatted(scenario.control))
                        .value(true));
    }

    @Test
    void enteringAnAnswerForAnUnexpectedReviewerOrUnknownStatusIsUnprocessable() throws Exception {
        start();
        UUID revision = ApiScenario.revisionId(scenario.submit(scenario.document("Plan", "Lot 01"), "A", null));

        mvc.perform(post("/api/projects/{p}/revisions/{r}/opinions", scenario.project, revision)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerId\":\"%s\",\"statusCode\":\"VSO\"}".formatted(scenario.architect)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/projects/{p}/revisions/{r}/opinions", scenario.project, revision)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerId\":\"%s\",\"statusCode\":\"ZZZ\"}".formatted(scenario.control)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void historyTellsTheWholeStoryInOrderAndNeverContainsAToken() throws Exception {
        start();
        MvcResult submitted = scenario.submit(scenario.document("Plan", "Lot 01"), "A", null);
        UUID revision = ApiScenario.revisionId(submitted);
        String token = ApiScenario.tokenOf(submitted, scenario.control);
        mvc.perform(get("/r/{t}", token));
        mvc.perform(post("/r/{t}/seen", token));
        mvc.perform(post("/r/{t}/opinion", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"statusCode\":\"VSO\"}"));

        MvcResult history = mvc.perform(get("/api/projects/{p}/revisions/{r}/history", scenario.project, revision)
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();

        List<String> types = com.jayway.jsonpath.JsonPath.read(history.getResponse().getContentAsString(), "$[*].type");
        assertThat(types).startsWith("RevisionSubmitted", "ReviewOpened")
                .contains("ReviewLinkIssued", "ReviewLinkOpened", "OpinionGiven");
        assertThat(history.getResponse().getContentAsString()).doesNotContain(token);
    }

    // ---- dashboard ---------------------------------------------------------------------------

    @Test
    void dashboardDerivesEveryStatusAndCountsThem() throws Exception {
        start();
        UUID empty = scenario.document("Sans révision", "Lot 01");
        UUID pending = scenario.document("En attente", "Lot 01");
        UUID approved = scenario.document("Approuvé", "Lot 01");
        UUID blocked = scenario.document("Refusé", "Lot 01");
        UUID informed = scenario.document("Pour information", "Lot 01");

        scenario.submit(pending, "A", null);
        answerAll(scenario.submit(approved, "A", null), "VSO", "VSO");
        answerAll(scenario.submit(blocked, "A", null), "VSO", "NC");
        MvcResult info = scenario.submit(informed, "A", List.of(scenario.architect));
        answer(ApiScenario.tokenOf(info, scenario.architect), "VPI");
        clock.advance(java.time.Duration.ofDays(3));

        MvcResult result = mvc.perform(get("/api/projects/{p}/dashboard", scenario.project).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents", hasSize(5)))
                .andExpect(jsonPath("$.totals.NO_REVISION").value(1))
                .andExpect(jsonPath("$.totals.PENDING").value(1))
                .andExpect(jsonPath("$.totals.APPROVED").value(1))
                .andExpect(jsonPath("$.totals.TO_REVISE").value(1))
                .andExpect(jsonPath("$.totals.ACKNOWLEDGED").value(1))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(statusOfDocument(body, "Sans révision")).isEqualTo("NO_REVISION");
        assertThat(statusOfDocument(body, "En attente")).isEqualTo("PENDING");
        assertThat(statusOfDocument(body, "Approuvé")).isEqualTo("APPROVED");
        assertThat(statusOfDocument(body, "Refusé")).isEqualTo("TO_REVISE");
        assertThat(statusOfDocument(body, "Pour information")).isEqualTo("ACKNOWLEDGED");
        // Nobody answered "En attente", three days after it was sent.
        assertThat(com.jayway.jsonpath.JsonPath.<List<Integer>>read(body,
                "$.documents[?(@.title=='En attente')].reviewers[*].waitingDays")).containsOnly(3);
        assertThat(empty).isNotNull();
    }

    @Test
    void dashboardFiltersByWorkPackageAndStatus() throws Exception {
        start();
        scenario.submit(scenario.document("A", "Lot 01"), "A", null);
        scenario.document("B", null);

        mvc.perform(get("/api/projects/{p}/dashboard", scenario.project).param("workPackage", "Lot 01")
                        .session(session))
                .andExpect(jsonPath("$.documents", hasSize(1)));
        mvc.perform(get("/api/projects/{p}/dashboard", scenario.project).param("status", "NO_REVISION")
                        .session(session))
                .andExpect(jsonPath("$.documents", hasSize(1)))
                .andExpect(jsonPath("$.documents[0].title").value("B"));
    }

    @Test
    void dashboardShowsTheNewestRevisionAndFlagsNobodyAsLateWhoWasNotExpected() throws Exception {
        start();
        UUID document = scenario.document("Plan", "Lot 01");
        scenario.submit(document, "A", null);
        MvcResult second = scenario.submit(document, "B", List.of(scenario.control));
        answer(ApiScenario.tokenOf(second, scenario.control), "VSO");

        mvc.perform(get("/api/projects/{p}/dashboard", scenario.project).session(session))
                .andExpect(jsonPath("$.documents[0].revision.label").value("B"))
                .andExpect(jsonPath("$.documents[0].revisionCount").value(2))
                .andExpect(jsonPath("$.documents[0].status").value("APPROVED"))
                // Only the control office was asked on B: the structure office is not even listed.
                .andExpect(jsonPath("$.documents[0].reviewers", hasSize(1)));
    }

    // ---- authorization and leaks -------------------------------------------------------------

    @Test
    void anOutsiderGetsNotFoundOnEveryDocumentAndReviewRoute() throws Exception {
        start();
        UUID document = scenario.document("Plan", "Lot 01");
        MvcResult submitted = scenario.submit(document, "A", null);
        UUID revision = ApiScenario.revisionId(submitted);
        MockHttpSession outsider = signIn(newUser());
        String json = MediaType.APPLICATION_JSON_VALUE;

        List<MvcResult> results = List.of(
                mvc.perform(get("/api/projects/{p}/documents", scenario.project).session(outsider)).andReturn(),
                mvc.perform(post("/api/projects/{p}/documents", scenario.project).with(csrf()).session(outsider)
                        .contentType(json).content("{\"title\":\"X\"}")).andReturn(),
                mvc.perform(get("/api/projects/{p}/documents/{d}", scenario.project, document).session(outsider))
                        .andReturn(),
                mvc.perform(multipart("/api/projects/{p}/documents/{d}/revisions", scenario.project, document)
                        .file(new MockMultipartFile("file", "p.pdf", "application/pdf", TestFixtures.PDF))
                        .param("label", "Z").with(csrf()).session(outsider)).andReturn(),
                mvc.perform(get("/api/projects/{p}/revisions/{r}/file", scenario.project, revision).session(outsider))
                        .andReturn(),
                mvc.perform(get("/api/projects/{p}/revisions/{r}/review", scenario.project, revision)
                        .session(outsider)).andReturn(),
                mvc.perform(get("/api/projects/{p}/revisions/{r}/history", scenario.project, revision)
                        .session(outsider)).andReturn(),
                mvc.perform(post("/api/projects/{p}/revisions/{r}/reviewers/{x}/link", scenario.project, revision,
                        scenario.control).with(csrf()).session(outsider)).andReturn(),
                mvc.perform(post("/api/projects/{p}/revisions/{r}/reviewers/{x}/link/revoke", scenario.project,
                        revision, scenario.control).with(csrf()).session(outsider)).andReturn(),
                mvc.perform(post("/api/projects/{p}/revisions/{r}/opinions", scenario.project, revision)
                        .with(csrf()).session(outsider).contentType(json)
                        .content("{\"reviewerId\":\"%s\",\"statusCode\":\"VSO\"}".formatted(scenario.control)))
                        .andReturn(),
                mvc.perform(get("/api/projects/{p}/dashboard", scenario.project).session(outsider)).andReturn());

        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private void answer(String token, String statusCode) throws Exception {
        mvc.perform(post("/r/{t}/opinion", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusCode\":\"%s\"}".formatted(statusCode)))
                .andExpect(status().isOk());
    }

    private void answerAll(MvcResult submitted, String controlStatus, String structureStatus) throws Exception {
        answer(ApiScenario.tokenOf(submitted, scenario.control), controlStatus);
        answer(ApiScenario.tokenOf(submitted, scenario.structure), structureStatus);
    }

    private static String statusOfDocument(String dashboardJson, String title) {
        List<String> statuses = com.jayway.jsonpath.JsonPath.read(dashboardJson,
                "$.documents[?(@.title=='%s')].status".formatted(title));
        return statuses.get(0);
    }
}
