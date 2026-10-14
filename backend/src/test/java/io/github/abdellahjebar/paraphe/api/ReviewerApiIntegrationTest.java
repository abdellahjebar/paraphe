package io.github.abdellahjebar.paraphe.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewToken;
import io.github.abdellahjebar.paraphe.support.ApiScenario;
import io.github.abdellahjebar.paraphe.support.ApiTestBase;
import io.github.abdellahjebar.paraphe.support.TestFixtures;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/** The public side: what a reviewer can do with a link, and what they must never be able to learn. */
class ReviewerApiIntegrationTest extends ApiTestBase {

    private MockHttpSession session;
    private ApiScenario scenario;
    private UUID document;
    private MvcResult submitted;
    private String controlToken;
    private String structureToken;

    @BeforeEach
    void setUp() throws Exception {
        session = signIn(newUser());
        scenario = new ApiScenario(mvc, session).withProject();
        document = scenario.document("Plan de poutres", "Lot 01");
        submitted = scenario.submit(document, "A", null);
        controlToken = ApiScenario.tokenOf(submitted, scenario.control);
        structureToken = ApiScenario.tokenOf(submitted, scenario.structure);
    }

    private static String answerJson(String statusCode, String comment) {
        return "{\"statusCode\":\"%s\",\"comment\":\"%s\"}".formatted(statusCode, comment);
    }

    @Test
    void reviewerSeesTheDocumentAndTheStatusesTheyMayGiveAndNothingElse() throws Exception {
        MvcResult result = mvc.perform(get("/r/{t}", controlToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentTitle").value("Plan de poutres"))
                .andExpect(jsonPath("$.revisionLabel").value("A"))
                .andExpect(jsonPath("$.revisionSuperseded").value(false))
                .andExpect(jsonPath("$.availableStatuses", hasSize(5)))
                .andReturn();

        Map<String, Object> page = JsonPath.read(result.getResponse().getContentAsString(), "$");
        // No global status, no other reviewers, no project, no identifiers of the revision.
        assertThat(page.keySet()).containsExactlyInAnyOrder("documentTitle", "revisionLabel",
                "revisionSequence", "revisionSuperseded", "linkExpiresAt", "availableStatuses", "myLastOpinion");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(scenario.structure.toString())
                .doesNotContain(scenario.project.toString());
    }

    @Test
    void reviewerDownloadsTheExactFile() throws Exception {
        MvcResult result = mvc.perform(get("/r/{t}/document", controlToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(TestFixtures.PDF);
    }

    @Test
    void reviewerAnswersAndIsToldOnlyThatItWasRecorded() throws Exception {
        MvcResult result = mvc.perform(post("/r/{t}/opinion", controlToken)
                        .contentType(MediaType.APPLICATION_JSON).content(answerJson("VAO", "à compléter")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorded").value(true))
                .andExpect(jsonPath("$.revisionSuperseded").value(false))
                .andReturn();

        // The global status describes the other reviewers too, so it is not returned.
        Map<String, Object> body = JsonPath.read(result.getResponse().getContentAsString(), "$");
        assertThat(body.keySet()).containsExactlyInAnyOrder("recorded", "revisionSuperseded");
    }

    @Test
    void theAnswerShowsUpForTheContractorWithTheRightStatus() throws Exception {
        mvc.perform(post("/r/{t}/opinion", controlToken).contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("VSO", ""))).andExpect(status().isOk());
        mvc.perform(post("/r/{t}/opinion", structureToken).contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("VAO", "fiche technique"))).andExpect(status().isOk());

        mvc.perform(get("/api/projects/{p}/dashboard", scenario.project).session(session))
                .andExpect(jsonPath("$.documents[0].status").value("APPROVED_WITH_RESERVES"));
    }

    @Test
    void afterAnsweringTheReviewerSeesTheirOwnLastAnswer() throws Exception {
        mvc.perform(post("/r/{t}/opinion", controlToken).contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("VAO", "à compléter"))).andExpect(status().isOk());

        mvc.perform(get("/r/{t}", controlToken))
                .andExpect(jsonPath("$.myLastOpinion.statusCode").value("VAO"))
                .andExpect(jsonPath("$.myLastOpinion.comment").value("à compléter"))
                .andExpect(jsonPath("$.myLastOpinion.enteredByOther").value(false));
    }

    @Test
    void anAnswerIsAppendedNotReplacedWhenTheReviewerChangesTheirMind() throws Exception {
        mvc.perform(post("/r/{t}/opinion", controlToken).contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("NC", "non conforme"))).andExpect(status().isOk());
        mvc.perform(post("/r/{t}/opinion", controlToken).contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("VSO", "corrigé"))).andExpect(status().isOk());
        UUID revision = ApiScenario.revisionId(submitted);

        MvcResult history = mvc.perform(get("/api/projects/{p}/revisions/{r}/history", scenario.project, revision)
                .session(session)).andReturn();

        List<String> types = JsonPath.read(history.getResponse().getContentAsString(), "$[*].type");
        assertThat(types.stream().filter("OpinionGiven"::equals).count()).isEqualTo(2);
    }

    @Test
    void unusableLinksAllGiveTheSameAnswerWhateverTheReason() throws Exception {
        String unknown = ReviewToken.generate().value();
        MvcResult unknownResult = mvc.perform(get("/r/{t}", unknown)).andExpect(status().isNotFound()).andReturn();

        mvc.perform(post("/api/projects/{p}/revisions/{r}/reviewers/{x}/link/revoke", scenario.project,
                        ApiScenario.revisionId(submitted), scenario.control)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .session(session)).andExpect(status().isNoContent());
        MvcResult revokedResult = mvc.perform(get("/r/{t}", controlToken)).andExpect(status().isNotFound()).andReturn();

        clock.advance(Duration.ofDays(31));
        MvcResult expiredResult = mvc.perform(get("/r/{t}", structureToken)).andExpect(status().isNotFound())
                .andReturn();

        String expected = unknownResult.getResponse().getContentAsString();
        assertThat(revokedResult.getResponse().getContentAsString()).isEqualTo(expected);
        assertThat(expiredResult.getResponse().getContentAsString()).isEqualTo(expected);
        assertThat(expected).doesNotContain(unknown).doesNotContain(controlToken);
    }

    @Test
    void everyReviewerRouteRefusesAnUnusableLink() throws Exception {
        String unknown = ReviewToken.generate().value();

        mvc.perform(get("/r/{t}/document", unknown)).andExpect(status().isNotFound());
        mvc.perform(post("/r/{t}/opinion", unknown).contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("VSO", ""))).andExpect(status().isNotFound());
    }

    @Test
    void unknownStatusAndOversizedCommentAreRefused() throws Exception {
        mvc.perform(post("/r/{t}/opinion", controlToken).contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson("ZZZ", "")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/r/{t}/opinion", controlToken).contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson("VSO", "x".repeat(5001))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownFieldsInTheAnswerAreRejected() throws Exception {
        mvc.perform(post("/r/{t}/opinion", controlToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statusCode\":\"VSO\",\"reviewerId\":\"%s\"}".formatted(scenario.structure)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aReviewerCannotUseTheirLinkToAnswerForSomeoneElse() throws Exception {
        mvc.perform(post("/r/{t}/opinion", controlToken).contentType(MediaType.APPLICATION_JSON)
                .content(answerJson("VSO", ""))).andExpect(status().isOk());

        mvc.perform(get("/api/projects/{p}/revisions/{r}/review", scenario.project, ApiScenario.revisionId(submitted))
                        .session(session))
                .andExpect(jsonPath("$.reviewers[?(@.reviewerId=='%s')].answered".formatted(scenario.structure))
                        .value(false))
                .andExpect(jsonPath("$.reviewers[?(@.reviewerId=='%s')].answered".formatted(scenario.control))
                        .value(true));
    }

    @Test
    void answeringAnOlderRevisionIsKeptAndFlagged() throws Exception {
        scenario.submit(document, "B", null);

        mvc.perform(get("/r/{t}", controlToken)).andExpect(jsonPath("$.revisionSuperseded").value(true));
        mvc.perform(post("/r/{t}/opinion", controlToken).contentType(MediaType.APPLICATION_JSON)
                        .content(answerJson("VSO", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionSuperseded").value(true));
    }

    @Test
    void errorBodiesOnReviewerRoutesNeverEchoTheToken() throws Exception {
        MvcResult result = mvc.perform(get("/r/{t}/no-such-thing", controlToken)).andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(controlToken);
    }

    @Test
    void reviewerRoutesNeverSetCookiesOrKeepASession() throws Exception {
        MvcResult result = mvc.perform(get("/r/{t}", controlToken)).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(result.getRequest().getSession(false)).isNull();
    }
}
