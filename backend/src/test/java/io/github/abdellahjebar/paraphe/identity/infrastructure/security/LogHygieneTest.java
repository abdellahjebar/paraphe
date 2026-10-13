package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.reviews.domain.ReviewToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * A review token is a credential. It must not end up in the application log, whatever happens to
 * the request that carried it.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class LogHygieneTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RequestRateLimiter reviewerLimiter;

    @Test
    void tokenDoesNotAppearInTheLogForAnyOutcome(CapturedOutput output) throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        reviewerLimiter.clear();
        String token = ReviewToken.generate().value();

        mvc.perform(get("/r/" + token));                       // served
        mvc.perform(get("/r/" + token + "/missing"));          // not found
        mvc.perform(get("/r/" + token).header("X-Forwarded-For", "203.0.113.9"));
        for (int i = 0; i < 70; i++) {                          // rate limited
            mvc.perform(get("/r/" + token));
        }

        assertThat(output.getAll()).doesNotContain(token);
    }
}
