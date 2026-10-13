package io.github.abdellahjebar.paraphe.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.identity.application.UserAdminService;
import io.github.abdellahjebar.paraphe.identity.domain.AppUser;
import io.github.abdellahjebar.paraphe.identity.infrastructure.security.RequestRateLimiter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * Shared set-up for tests that call the HTTP API through the real security filters and a real
 * database: real accounts, real sessions, real CSRF handling.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
public abstract class ApiTestBase {

    protected static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    protected UserAdminService admin;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private RequestRateLimiter reviewerLimiter;

    protected MockMvc mvc;

    @BeforeEach
    void setUpApi() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clock.reset();
        reviewerLimiter.clear();
    }

    protected AppUser newUser() {
        return admin.createUser("user-" + UUID.randomUUID() + "@example.org", "Utilisateur", PASSWORD, false, "test");
    }

    protected MockHttpSession signIn(AppUser user) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(user.email(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected static String read(MvcResult result, String jsonPath) throws Exception {
        Object value = JsonPath.read(result.getResponse().getContentAsString(), jsonPath);
        return String.valueOf(value);
    }

    protected static UUID readId(MvcResult result, String jsonPath) throws Exception {
        return UUID.fromString(read(result, jsonPath));
    }
}
