package io.github.abdellahjebar.paraphe.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abdellahjebar.paraphe.shared.web.ErrorCode;
import io.github.abdellahjebar.paraphe.support.ApiScenario;
import io.github.abdellahjebar.paraphe.support.ApiTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Every error a client can meet carries a stable machine-readable code. */
class ErrorCodesIntegrationTest extends ApiTestBase {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void anonymousCallerGetsAuthenticationRequired() throws Exception {
        mvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTHENTICATION_REQUIRED.name()));
    }

    @Test
    void writeWithoutCsrfTokenGetsForbidden() throws Exception {
        MockHttpSession session = signIn(newUser());
        mvc.perform(post("/api/projects").session(session).contentType(JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.name()));
    }

    @Test
    void unknownProjectOrNotAMemberIsNotFound() throws Exception {
        MockHttpSession session = signIn(newUser());
        mvc.perform(get("/api/projects/{id}", UUID.randomUUID()).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));
    }

    @Test
    void blankNameIsAValidationFailureWithTheFieldAndNoValue() throws Exception {
        MockHttpSession session = signIn(newUser());
        MvcResult result = mvc.perform(post("/api/projects").with(csrf()).session(session)
                        .contentType(JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("   ");
    }

    @Test
    void tooLongValueIsReportedAsTooLong() throws Exception {
        MockHttpSession session = signIn(newUser());
        mvc.perform(post("/api/projects").with(csrf()).session(session)
                        .contentType(JSON).content("{\"name\":\"" + "a".repeat(5000) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
    }

    @Test
    void unreadableBodyIsMalformedRequest() throws Exception {
        MockHttpSession session = signIn(newUser());
        mvc.perform(post("/api/projects").with(csrf()).session(session).contentType(JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.MALFORMED_REQUEST.name()));
    }

    @Test
    void wrongMethodIsMethodNotAllowed() throws Exception {
        MockHttpSession session = signIn(newUser());
        mvc.perform(delete("/api/projects").with(csrf()).session(session))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(ErrorCode.METHOD_NOT_ALLOWED.name()));
    }

    @Test
    void duplicateWorkPackageIsAConflict() throws Exception {
        MockHttpSession session = signIn(newUser());
        ApiScenario scenario = new ApiScenario(mvc, session).withProject();
        // withProject already created "Lot 01"
        mvc.perform(post("/api/projects/{p}/work-packages", scenario.project).with(csrf()).session(session)
                        .contentType(JSON).content("{\"code\":\"Lot 01\",\"label\":\"Again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_CODE.name()));
    }

    @Test
    void notAPdfIsRefusedWithItsOwnCode() throws Exception {
        MockHttpSession session = signIn(newUser());
        ApiScenario scenario = new ApiScenario(mvc, session).withProject();
        UUID document = scenario.document("Plan", "Lot 01");
        MockMultipartFile file = new MockMultipartFile("file", "plan.pdf", "application/pdf",
                "this is not a pdf".getBytes());
        mvc.perform(multipart("/api/projects/{p}/documents/{d}/revisions", scenario.project, document)
                        .file(file).param("label", "A").with(csrf()).session(session))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_A_PDF.name()));
    }

    @Test
    void weakPasswordNamesTheReasonAndTheLimit() throws Exception {
        MockHttpSession session = signIn(newUser());
        mvc.perform(post("/api/auth/password").with(csrf()).session(session).contentType(JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.WEAK_PASSWORD.name()))
                .andExpect(jsonPath("$.reason").value("TOO_SHORT"))
                .andExpect(jsonPath("$.limit").isNumber());
    }

    @Test
    void wrongCurrentPasswordHasItsOwnCode() throws Exception {
        MockHttpSession session = signIn(newUser());
        mvc.perform(post("/api/auth/password").with(csrf()).session(session).contentType(JSON)
                        .content("{\"currentPassword\":\"not the password\",\"newPassword\":\"a long enough new passphrase\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CURRENT_PASSWORD.name()));
    }

    @Test
    void wrongLoginIsInvalidCredentials() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(JSON)
                        .content("{\"email\":\"nobody@example.test\",\"password\":\"whatever it is\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.name()));
    }

    @Test
    void unusableReviewerLinkIsNotFoundAndNeverEchoesTheToken() throws Exception {
        String token = "tokenthatdoesnotexisttokenthatdoesnotexist1234";
        MvcResult result = mvc.perform(get("/r/{t}", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(token);
    }
}
