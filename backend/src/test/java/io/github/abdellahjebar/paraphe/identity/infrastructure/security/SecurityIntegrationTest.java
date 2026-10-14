package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.identity.application.UserAdminService;
import io.github.abdellahjebar.paraphe.identity.domain.AppUser;
import io.github.abdellahjebar.paraphe.projects.application.ProjectService;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewToken;
import io.github.abdellahjebar.paraphe.support.MutableClock;
import io.github.abdellahjebar.paraphe.support.TestClockConfiguration;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * One test per threat. These run against the real filter chains with a real database.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class SecurityIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserAdminService admin;

    @Autowired
    private ProjectService projects;

    @Autowired
    private InMemoryLoginAttemptLimiter loginLimiter;

    @Autowired
    private RequestRateLimiter reviewerLimiter;

    @Autowired
    private MutableClock clock;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clock.reset();
        loginLimiter.clear();
        reviewerLimiter.clear();
    }

    private String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.org";
    }

    private AppUser account(boolean mustChange) {
        return admin.createUser(newEmail(), "Khawla", PASSWORD, mustChange, "test");
    }

    private static String loginJson(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private MockHttpSession signIn(AppUser user, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(user.email(), password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    // ---- who may reach what ------------------------------------------------------------------

    @Test
    void apiRefusesAnonymousCallersWithAJsonProblem() throws Exception {
        mvc.perform(get("/api/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void reviewerPathsNeedNoSession() throws Exception {
        // Reached without any session: an unknown token is "not found", not "please sign in".
        mvc.perform(get("/r/" + ReviewToken.generate().value()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anythingElseIsRefused() throws Exception {
        mvc.perform(get("/other")).andExpect(status().is4xxClientError());
        mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
    }

    @Test
    void theApiContractIsNotServedByDefault() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().is4xxClientError());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is4xxClientError());
    }

    @Test
    void healthProbeIsPublicAndRevealsNoDetail() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    // ---- signing in --------------------------------------------------------------------------

    @Test
    void validCredentialsOpenASessionThatIdentifiesTheUser() throws Exception {
        AppUser user = account(false);

        MockHttpSession session = signIn(user, PASSWORD);

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        mvc.perform(get("/api/probe").session(session)).andExpect(status().isOk());
    }

    @Test
    void responsesNeverContainAPasswordOrAHash() throws Exception {
        AppUser user = account(false);

        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(user.email(), PASSWORD)))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(PASSWORD).doesNotContain("$2a$").doesNotContainIgnoringCase("password\":\"");
    }

    @Test
    void wrongPasswordAndUnknownAccountAreIndistinguishable() throws Exception {
        AppUser user = account(false);

        MvcResult wrong = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(user.email(), "not the right passphrase"))).andReturn();
        MvcResult unknown = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(newEmail(), "not the right passphrase"))).andReturn();

        assertThat(wrong.getResponse().getStatus()).isEqualTo(401).isEqualTo(unknown.getResponse().getStatus());
        assertThat(wrong.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
    }

    @Test
    void tooManyFailuresLockTheAccountAndThenItRecovers() throws Exception {
        AppUser user = account(false);
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(user.email(), "wrong passphrase " + i))).andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.email(), PASSWORD)))
                .andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofMinutes(16));
        signIn(user, PASSWORD);
    }

    @Test
    void signingInReplacesTheSessionIdentifier() throws Exception {
        AppUser user = account(false);
        MockHttpSession before = new MockHttpSession();
        String idBefore = before.getId();

        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf()).session(before)
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(user.email(), PASSWORD)))
                .andExpect(status().isOk()).andReturn();

        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(idBefore);
    }

    @Test
    void signingOutEndsTheSession() throws Exception {
        MockHttpSession session = signIn(account(false), PASSWORD);

        mvc.perform(post("/api/auth/logout").with(csrf()).session(session)).andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
    }

    // ---- forged requests ---------------------------------------------------------------------

    @Test
    void stateChangingCallWithoutACsrfTokenIsRefused() throws Exception {
        MockHttpSession session = signIn(account(false), PASSWORD);

        mvc.perform(post("/api/probe/write").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/probe/write").session(session).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void signingInItselfRequiresACsrfToken() throws Exception {
        AppUser user = account(false);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.email(), PASSWORD)))
                .andExpect(status().isForbidden());
    }

    // ---- a new account must choose its own password ----------------------------------------------

    @Test
    void anAccountThatMustChangeItsPasswordCanDoNothingElseUntilItDoes() throws Exception {
        AppUser user = account(true);
        MockHttpSession session = signIn(user, PASSWORD);

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(get("/api/probe").session(session)).andExpect(status().isForbidden());

        mvc.perform(post("/api/auth/password").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"a brand new passphrase\"}"
                                .formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        mvc.perform(get("/api/probe").session(session)).andExpect(status().isOk());
    }

    @Test
    void weakNewPasswordIsRefusedWithAnExplanation() throws Exception {
        MockHttpSession session = signIn(account(false), PASSWORD);

        mvc.perform(post("/api/auth/password").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"short\"}".formatted(PASSWORD)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---- projects belong to their members --------------------------------------------------------

    @Test
    void aMemberSeesTheirProjectAndAnOutsiderSeesNothing() throws Exception {
        AppUser owner = account(false);
        AppUser outsider = account(false);
        Project project = projects.createProjectFor(owner.id().value(), "Chantier " + UUID.randomUUID(), "test");
        MockHttpSession ownerSession = signIn(owner, PASSWORD);
        MockHttpSession outsiderSession = signIn(outsider, PASSWORD);

        mvc.perform(get("/api/probe/projects/{id}", project.id().value()).session(ownerSession))
                .andExpect(status().isOk());

        MvcResult denied = mvc.perform(get("/api/probe/projects/{id}", project.id().value()).session(outsiderSession))
                .andExpect(status().isNotFound()).andReturn();
        MvcResult missing = mvc.perform(get("/api/probe/projects/{id}", UUID.randomUUID()).session(outsiderSession))
                .andExpect(status().isNotFound()).andReturn();

        // A project you cannot use looks exactly like a project that does not exist.
        assertThat(denied.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
    }

    // ---- review links -----------------------------------------------------------------------------

    @Test
    void reviewerResponsesAreNeverCachedAndNeverLeakTheLinkThroughReferer() throws Exception {
        mvc.perform(get("/r/" + ReviewToken.generate().value()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-ancestors 'none'")));
    }

    @Test
    void reviewerPathsAreRateLimitedPerAddress() throws Exception {
        String path = "/r/" + ReviewToken.generate().value();
        for (int i = 0; i < 60; i++) {
            mvc.perform(get(path)).andExpect(status().isNotFound());
        }

        mvc.perform(get(path))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
        // The contractor's API is not affected by reviewer traffic.
        mvc.perform(get("/api/probe")).andExpect(status().isUnauthorized());
    }

    @Test
    void errorBodiesNeverEchoTheLinkToken() throws Exception {
        String token = ReviewToken.generate().value();

        MvcResult result = mvc.perform(get("/r/" + token + "/does-not-exist")).andReturn();

        assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(token);
    }

    @Test
    void apiHeadersForbidFramingAndReferer() throws Exception {
        mvc.perform(get("/api/probe"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
