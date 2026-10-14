package io.github.abdellahjebar.paraphe.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abdellahjebar.paraphe.support.ApiTestBase;
import org.junit.jupiter.api.Test;

/** The application serves its own interface, and still refuses everything it does not know. */
class WebShellIntegrationTest extends ApiTestBase {

    @Test
    void interfaceRoutesAnswerWithTheEntryPage() throws Exception {
        for (String path : new String[] {"/", "/connexion", "/projets", "/projets/abc/suivi"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void entryPageIsHtmlWithAStrictPolicy() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
    }

    @Test
    void reviewerPageIsServedWithoutReferrerAndWithoutCache() throws Exception {
        mvc.perform(get("/relecture/some-token"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void unknownPathsAreStillRefused() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is4xxClientError());
        mvc.perform(get("/assets/../application.properties")).andExpect(status().is4xxClientError());
        mvc.perform(post("/projets")).andExpect(status().is4xxClientError());
    }
}
