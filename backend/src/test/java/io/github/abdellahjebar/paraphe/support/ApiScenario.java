package io.github.abdellahjebar.paraphe.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Builds a realistic project through the HTTP API itself (never by calling services), so tests
 * start from the same state a real user would have created.
 */
public final class ApiScenario {

    private final MockMvc mvc;
    private final MockHttpSession session;

    public UUID project;
    public UUID control;
    public UUID structure;
    public UUID architect;

    public ApiScenario(MockMvc mvc, MockHttpSession session) {
        this.mvc = mvc;
        this.session = session;
    }

    /** A project with a work package "Lot 01" whose defaults are the control office and the structure office. */
    public ApiScenario withProject() throws Exception {
        project = id(mvc.perform(post("/api/projects").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Chantier test\"}"))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(post("/api/projects/{p}/work-packages", project).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"Lot 01\",\"label\":\"Gros œuvre\"}"))
                .andExpect(status().isCreated());
        control = participant("Bureau de controle", "CONTROL_OFFICE", null);
        structure = participant("Bureau structure", "ENGINEERING_OFFICE", "Structure");
        architect = participant("Architecte", "ARCHITECT", null);
        mvc.perform(put("/api/projects/{p}/work-packages/Lot 01/default-reviewers", project).with(csrf())
                .session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewerIds\":[\"%s\",\"%s\"]}".formatted(control, structure)))
                .andExpect(status().isOk());
        return this;
    }

    public UUID participant(String name, String role, String specialty) throws Exception {
        String spec = specialty == null ? "" : ",\"specialty\":\"%s\"".formatted(specialty);
        return id(mvc.perform(post("/api/projects/{p}/participants", project).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"role\":\"%s\",\"email\":\"%s@example.org\"%s}".formatted(
                        name, role, UUID.randomUUID(), spec)))
                .andExpect(status().isCreated()).andReturn());
    }

    public UUID document(String title, String workPackage) throws Exception {
        String wp = workPackage == null ? "" : ",\"workPackage\":\"%s\"".formatted(workPackage);
        return id(mvc.perform(post("/api/projects/{p}/documents", project).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"%s\",\"type\":\"PLAN\"%s}".formatted(title, wp)))
                .andExpect(status().isCreated()).andReturn());
    }

    /** Deposits a revision and sends it; returns the raw response so tests can read links and ids. */
    public MvcResult submit(UUID document, String label, List<UUID> reviewers) throws Exception {
        var request = multipart("/api/projects/{p}/documents/{d}/revisions", project, document)
                .file(new MockMultipartFile("file", "plan.pdf", "application/pdf", TestFixtures.PDF))
                .param("label", label)
                .with(csrf()).session(session);
        if (reviewers != null) {
            for (UUID reviewer : reviewers) {
                request.param("reviewerIds", reviewer.toString());
            }
        }
        return mvc.perform(request).andExpect(status().isCreated()).andReturn();
    }

    public static String tokenOf(MvcResult submitted, UUID reviewer) throws Exception {
        List<String> urls = JsonPath.read(submitted.getResponse().getContentAsString(),
                "$.links[?(@.reviewerId=='%s')].url".formatted(reviewer));
        return urls.get(0).substring(urls.get(0).lastIndexOf('/') + 1);
    }

    public static UUID revisionId(MvcResult submitted) throws Exception {
        return UUID.fromString(JsonPath.read(submitted.getResponse().getContentAsString(), "$.revision.id"));
    }

    private static UUID id(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }
}
