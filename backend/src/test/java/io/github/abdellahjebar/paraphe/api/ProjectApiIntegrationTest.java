package io.github.abdellahjebar.paraphe.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.identity.domain.AppUser;
import io.github.abdellahjebar.paraphe.support.ApiTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class ProjectApiIntegrationTest extends ApiTestBase {

    @Autowired
    private AuditLog auditLog;

    private UUID createProject(MockHttpSession session, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/projects").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result, "$.id");
    }

    private UUID addParticipant(MockHttpSession session, UUID project, String name, String role) throws Exception {
        MvcResult result = mvc.perform(post("/api/projects/{id}/participants", project).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"role\":\"%s\",\"email\":\"%s@example.org\"}"
                                .formatted(name, role, name.toLowerCase().replace(' ', '.'))))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result, "$.id");
    }

    // ---- creating and listing ------------------------------------------------------------------

    @Test
    void creatingAProjectReturnsItAndMakesTheCallerAMember() throws Exception {
        MockHttpSession session = signIn(newUser());

        mvc.perform(post("/api/projects").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rénovation école\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/projects/")))
                .andExpect(jsonPath("$.name").value("Rénovation école"))
                .andExpect(jsonPath("$.createdAt").isString());

        mvc.perform(get("/api/projects").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Rénovation école"));
    }

    @Test
    void eachUserOnlyListsTheirOwnProjects() throws Exception {
        MockHttpSession mine = signIn(newUser());
        MockHttpSession other = signIn(newUser());
        createProject(mine, "Projet privé " + UUID.randomUUID());

        mvc.perform(get("/api/projects").session(other))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void waitingThresholdsHaveDefaultsAndCanBeChangedByMembersOnly() throws Exception {
        MockHttpSession mine = signIn(newUser());
        MockHttpSession other = signIn(newUser());
        UUID project = createProject(mine, "Seuils " + UUID.randomUUID());

        mvc.perform(get("/api/projects/{p}/dashboard", project).session(mine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thresholds.chaseFromDays").value(8))
                .andExpect(jsonPath("$.thresholds.lateFromDays").value(15));

        mvc.perform(put("/api/projects/{p}/thresholds", project).with(csrf()).session(mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chaseFromDays\":5,\"lateFromDays\":10}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/projects/{p}/dashboard", project).session(mine))
                .andExpect(jsonPath("$.thresholds.chaseFromDays").value(5))
                .andExpect(jsonPath("$.thresholds.lateFromDays").value(10));

        mvc.perform(put("/api/projects/{p}/thresholds", project).with(csrf()).session(mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chaseFromDays\":10,\"lateFromDays\":10}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(put("/api/projects/{p}/thresholds", project).with(csrf()).session(other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chaseFromDays\":5,\"lateFromDays\":10}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void newProjectComesWithTheDefaultStatuses() throws Exception {
        MockHttpSession session = signIn(newUser());
        UUID project = createProject(session, "Chantier");

        mvc.perform(get("/api/projects/{id}", project).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statuses[*].code").value(containsInAnyOrder("NC", "REF", "VAO", "VPI", "VSO")))
                .andExpect(jsonPath("$.workPackages").isEmpty())
                .andExpect(jsonPath("$.participants").isEmpty());
    }

    // ---- configuration -------------------------------------------------------------------------

    @Test
    void workPackageCanBeAddedButNotTwice() throws Exception {
        MockHttpSession session = signIn(newUser());
        UUID project = createProject(session, "Chantier");
        String body = "{\"code\":\"Lot 01\",\"label\":\"Gros œuvre\"}";

        mvc.perform(post("/api/projects/{id}/work-packages", project).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("Lot 01"));
        mvc.perform(post("/api/projects/{id}/work-packages", project).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void participantWithoutAnyContactIsRefused() throws Exception {
        MockHttpSession session = signIn(newUser());
        UUID project = createProject(session, "Chantier");

        mvc.perform(post("/api/projects/{id}/participants", project).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sans contact\",\"role\":\"ARCHITECT\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void participantRoleMustBeAKnownOne() throws Exception {
        MockHttpSession session = signIn(newUser());
        UUID project = createProject(session, "Chantier");

        mvc.perform(post("/api/projects/{id}/participants", project).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"role\":\"KING\",\"email\":\"x@example.org\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void defaultReviewersShowUpInTheOverview() throws Exception {
        MockHttpSession session = signIn(newUser());
        UUID project = createProject(session, "Chantier");
        mvc.perform(post("/api/projects/{id}/work-packages", project).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"Lot 01\",\"label\":\"Gros œuvre\"}"));
        UUID control = addParticipant(session, project, "Bureau Controle", "CONTROL_OFFICE");

        mvc.perform(put("/api/projects/{id}/work-packages/{code}/default-reviewers", project, "Lot 01")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerIds\":[\"%s\"]}".formatted(control)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultReviewerIds[0]").value(control.toString()));

        mvc.perform(get("/api/projects/{id}", project).session(session))
                .andExpect(jsonPath("$.workPackages[0].defaultReviewerIds[0]").value(control.toString()));
    }

    @Test
    void defaultReviewerFromAnotherProjectIsRefused() throws Exception {
        MockHttpSession session = signIn(newUser());
        UUID mine = createProject(session, "Chantier A");
        UUID other = createProject(session, "Chantier B");
        mvc.perform(post("/api/projects/{id}/work-packages", mine).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"Lot 01\"}"));
        UUID stranger = addParticipant(session, other, "Bureau Etranger", "CONTROL_OFFICE");

        mvc.perform(put("/api/projects/{id}/work-packages/{code}/default-reviewers", mine, "Lot 01")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerIds\":[\"%s\"]}".formatted(stranger)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void statusCanBeDefinedAndThenDeactivated() throws Exception {
        MockHttpSession session = signIn(newUser());
        UUID project = createProject(session, "Chantier");

        mvc.perform(post("/api/projects/{id}/statuses", project).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"VRF\",\"label\":\"Visé après reprise\",\"effect\":\"APPROVING\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.effect").value("APPROVING"));
        mvc.perform(get("/api/projects/{id}", project).session(session))
                .andExpect(jsonPath("$.statuses[*].code").value(hasItem("VRF")));

        mvc.perform(post("/api/projects/{id}/statuses/{code}/deactivate", project, "VRF").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/projects/{id}", project).session(session))
                .andExpect(jsonPath("$.statuses[*].code").value(not(hasItem("VRF"))));
    }

    @Test
    void duplicateStatusCodeIsAConflict() throws Exception {
        MockHttpSession session = signIn(newUser());
        UUID project = createProject(session, "Chantier");

        mvc.perform(post("/api/projects/{id}/statuses", project).with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"VSO\",\"label\":\"Autre\",\"effect\":\"APPROVING\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivatedParticipantDisappearsFromDefaults() throws Exception {
        MockHttpSession session = signIn(newUser());
        UUID project = createProject(session, "Chantier");
        mvc.perform(post("/api/projects/{id}/work-packages", project).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"Lot 01\"}"));
        UUID control = addParticipant(session, project, "Bureau Controle", "CONTROL_OFFICE");
        mvc.perform(put("/api/projects/{id}/work-packages/{code}/default-reviewers", project, "Lot 01")
                .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewerIds\":[\"%s\"]}".formatted(control)));

        mvc.perform(post("/api/projects/{id}/participants/{p}/deactivate", project, control)
                        .with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/projects/{id}", project).session(session))
                .andExpect(jsonPath("$.workPackages[0].defaultReviewerIds").isEmpty())
                .andExpect(jsonPath("$.participants[0].active").value(false));
    }

    // ---- input handling ------------------------------------------------------------------------

    @Test
    void unknownFieldsAreRejectedRatherThanIgnored() throws Exception {
        MockHttpSession session = signIn(newUser());

        mvc.perform(post("/api/projects").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"nmae\":\"typo\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankNameAndMalformedJsonAreBadRequests() throws Exception {
        MockHttpSession session = signIn(newUser());

        mvc.perform(post("/api/projects").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/projects").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedIdentifierIsABadRequestNotAServerError() throws Exception {
        MockHttpSession session = signIn(newUser());

        mvc.perform(get("/api/projects/not-a-uuid").session(session))
                .andExpect(status().is4xxClientError());
    }

    // ---- who may call what ---------------------------------------------------------------------

    @Test
    void anOutsiderGetsNotFoundOnEveryProjectRoute() throws Exception {
        MockHttpSession owner = signIn(newUser());
        MockHttpSession outsider = signIn(newUser());
        UUID project = createProject(owner, "Chantier privé");
        UUID participant = addParticipant(owner, project, "Bureau Controle", "CONTROL_OFFICE");
        String json = MediaType.APPLICATION_JSON_VALUE;

        List<MvcResult> results = List.of(
                mvc.perform(get("/api/projects/{id}", project).session(outsider)).andReturn(),
                mvc.perform(post("/api/projects/{id}/work-packages", project).with(csrf()).session(outsider)
                        .contentType(json).content("{\"code\":\"Lot 01\"}")).andReturn(),
                mvc.perform(post("/api/projects/{id}/participants", project).with(csrf()).session(outsider)
                        .contentType(json)
                        .content("{\"name\":\"X\",\"role\":\"ARCHITECT\",\"email\":\"x@example.org\"}")).andReturn(),
                mvc.perform(post("/api/projects/{id}/participants/{p}/deactivate", project, participant)
                        .with(csrf()).session(outsider)).andReturn(),
                mvc.perform(post("/api/projects/{id}/statuses", project).with(csrf()).session(outsider)
                        .contentType(json).content("{\"code\":\"X\",\"label\":\"X\",\"effect\":\"INFORMATIVE\"}"))
                        .andReturn(),
                mvc.perform(post("/api/projects/{id}/statuses/VSO/deactivate", project).with(csrf()).session(outsider))
                        .andReturn(),
                mvc.perform(put("/api/projects/{id}/work-packages/Lot 01/default-reviewers", project)
                        .with(csrf()).session(outsider).contentType(json).content("{\"reviewerIds\":[]}")).andReturn());

        MvcResult unknown = mvc.perform(get("/api/projects/{id}", UUID.randomUUID()).session(outsider)).andReturn();
        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus()).isEqualTo(404);
            // Same body as for a project that does not exist: nothing tells the two apart.
            assertThat(result.getResponse().getContentAsString())
                    .isEqualTo(unknown.getResponse().getContentAsString());
        }
    }

    @Test
    void anonymousCallersAreRefused() throws Exception {
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void writesWithoutACsrfTokenAreRefused() throws Exception {
        MockHttpSession session = signIn(newUser());

        mvc.perform(post("/api/projects").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    // ---- proof ---------------------------------------------------------------------------------

    @Test
    void theJournalNamesTheSignedInUserAsTheActor() throws Exception {
        AppUser user = newUser();
        MockHttpSession session = signIn(user);
        UUID project = createProject(session, "Chantier");
        addParticipant(session, project, "Bureau Controle", "CONTROL_OFFICE");

        List<AuditEvent> events = auditLog.findByTarget("PROJECT", project);

        assertThat(events).extracting(AuditEvent::type)
                .contains("ProjectCreated", "MemberGranted", "ParticipantAdded");
        assertThat(events).extracting(AuditEvent::actor).containsOnly("user:" + user.id().value());
    }

    @Test
    void errorBodiesNeverRepeatTheRequestPath() throws Exception {
        MockHttpSession session = signIn(newUser());
        UUID unknown = UUID.randomUUID();

        MvcResult result = mvc.perform(get("/api/projects/{id}", unknown).session(session)).andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(unknown.toString())
                .doesNotContain("/api/projects");
    }
}
