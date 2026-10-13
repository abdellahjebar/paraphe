package io.github.abdellahjebar.paraphe.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abdellahjebar.paraphe.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class ApiErrorHandlingTest extends ApiTestBase {

    @Test
    void unexpectedFailureIsAPlainErrorWithACorrelationIdAndNoDetail() throws Exception {
        MockHttpSession session = signIn(newUser());

        MvcResult result = mvc.perform(get("/api/probe/boom").session(session))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Internal error"))
                .andExpect(jsonPath("$.correlationId").isString())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("secret detail")
                .doesNotContain("IllegalStateException")
                .doesNotContain("at io.github");
    }

    @Test
    void wrongMethodIsAStandardProblemNotAServerError() throws Exception {
        MockHttpSession session = signIn(newUser());

        mvc.perform(delete("/api/projects").with(csrf()).session(session))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void unknownRouteIsNotFoundAndDoesNotEchoThePath() throws Exception {
        MockHttpSession session = signIn(newUser());

        MvcResult result = mvc.perform(get("/api/definitely-not-a-route").session(session)).andReturn();

        assertThat(result.getResponse().getStatus()).isIn(403, 404);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("definitely-not-a-route");
    }
}
