package io.github.abdellahjebar.paraphe.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.identity.application.UserAdminService;
import io.github.abdellahjebar.paraphe.support.TestFixtures;
import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * The whole product, through real HTTP, real cookies, real CSRF and a real database: the contractor
 * prepares a project and sends a revision, reviewers answer through their links, a refusal forces a
 * second revision, and the journal tells the story. Nothing here calls a service directly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class FullReviewCycleTest {

    private static final String PASSWORD = "correct horse battery staple";

    @LocalServerPort
    private int port;

    @Autowired
    private UserAdminService admin;

    /** A browser-like client: it keeps cookies and echoes the CSRF token on writes. */
    private final class Client {
        private final CookieManager cookies = new CookieManager();
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpResponse<byte[]> get(String path) throws Exception {
            return send(request(path).GET().build());
        }

        HttpResponse<byte[]> postJson(String path, String json) throws Exception {
            return send(writing(path).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build());
        }

        HttpResponse<byte[]> putJson(String path, String json) throws Exception {
            return send(writing(path).header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json)).build());
        }

        HttpResponse<byte[]> post(String path) throws Exception {
            return send(writing(path).POST(HttpRequest.BodyPublishers.noBody()).build());
        }

        HttpResponse<byte[]> upload(String path, String label, byte[] file, List<UUID> reviewers) throws Exception {
            String boundary = "----paraphe" + UUID.randomUUID();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            field(out, boundary, "label", label);
            for (UUID reviewer : reviewers) {
                field(out, boundary, "reviewerIds", reviewer.toString());
            }
            out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; "
                    + "filename=\"plan.pdf\"\r\nContent-Type: application/pdf\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.writeBytes(file);
            out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return send(writing(path).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build());
        }

        /** The first call hands out the CSRF cookie, exactly as it does for the real front end. */
        void signIn(String email) throws Exception {
            get("/api/auth/me");
            HttpResponse<byte[]> response = postJson("/api/auth/login",
                    "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
            assertThat(response.statusCode()).isEqualTo(200);
        }

        private HttpRequest.Builder request(String path) {
            return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        }

        private HttpRequest.Builder writing(String path) {
            HttpRequest.Builder builder = request(path);
            cookies.getCookieStore().getCookies().stream()
                    .filter(c -> c.getName().equals("XSRF-TOKEN")).findFirst().map(HttpCookie::getValue)
                    .ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
            return builder;
        }

        private HttpResponse<byte[]> send(HttpRequest request) throws Exception {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }

        private void field(ByteArrayOutputStream out, String boundary, String name, String value) {
            out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                    + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String text(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private static <T> T read(HttpResponse<byte[]> response, String path) {
        return JsonPath.read(text(response), path);
    }

    private static UUID id(HttpResponse<byte[]> response, String path) {
        return UUID.fromString(read(response, path));
    }

    private static String tokenFor(HttpResponse<byte[]> submitted, UUID reviewer) {
        List<String> urls = JsonPath.read(text(submitted), "$.links[?(@.reviewerId=='%s')].url".formatted(reviewer));
        return urls.get(0).substring(urls.get(0).lastIndexOf('/') + 1);
    }

    private static String answer(String statusCode, String comment) {
        return "{\"statusCode\":\"%s\",\"comment\":\"%s\"}".formatted(statusCode, comment);
    }

    @Test
    void theWholeReviewCycle() throws Exception {
        String khawlaEmail = "khawla-" + UUID.randomUUID() + "@example.org";
        String outsiderEmail = "outsider-" + UUID.randomUUID() + "@example.org";
        admin.createUser(khawlaEmail, "Khawla", PASSWORD, false, "test");
        admin.createUser(outsiderEmail, "Autre", PASSWORD, false, "test");
        List<String> everyTokenIssued = new ArrayList<>();

        // ---- the contractor prepares the project -------------------------------------------------
        Client khawla = new Client();
        khawla.signIn(khawlaEmail);

        UUID project = id(khawla.postJson("/api/projects", "{\"name\":\"Rénovation de l'école\"}"), "$.id");
        assertThat(khawla.postJson("/api/projects/%s/work-packages".formatted(project),
                "{\"code\":\"Lot 01\",\"label\":\"Gros œuvre\"}").statusCode()).isEqualTo(201);
        UUID control = id(khawla.postJson("/api/projects/%s/participants".formatted(project),
                "{\"name\":\"Bureau de contrôle\",\"role\":\"CONTROL_OFFICE\",\"email\":\"controle@example.org\"}"),
                "$.id");
        UUID structure = id(khawla.postJson("/api/projects/%s/participants".formatted(project),
                "{\"name\":\"Bureau d'études\",\"role\":\"ENGINEERING_OFFICE\",\"specialty\":\"Structure\","
                        + "\"email\":\"structure@example.org\"}"), "$.id");
        UUID architect = id(khawla.postJson("/api/projects/%s/participants".formatted(project),
                "{\"name\":\"Architecte\",\"role\":\"ARCHITECT\",\"phone\":\"+212600000000\"}"), "$.id");
        assertThat(khawla.putJson("/api/projects/%s/work-packages/Lot 01/default-reviewers".formatted(project)
                        .replace(" ", "%20"),
                "{\"reviewerIds\":[\"%s\",\"%s\"]}".formatted(control, structure)).statusCode()).isEqualTo(200);

        UUID document = id(khawla.postJson("/api/projects/%s/documents".formatted(project),
                "{\"title\":\"Plan de ferraillage des poutres\",\"workPackage\":\"Lot 01\",\"type\":\"PLAN\"}"),
                "$.id");

        // ---- she sends revision A; the lot's defaults decide who is asked ------------------------
        HttpResponse<byte[]> sentA = khawla.upload(
                "/api/projects/%s/documents/%s/revisions".formatted(project, document), "A", TestFixtures.PDF, List.of());
        assertThat(sentA.statusCode()).isEqualTo(201);
        UUID revisionA = id(sentA, "$.revision.id");
        String controlTokenA = tokenFor(sentA, control);
        String structureTokenA = tokenFor(sentA, structure);
        everyTokenIssued.addAll(List.of(controlTokenA, structureTokenA));
        assertThat(JsonPath.<List<String>>read(text(sentA), "$.links[*].reviewerId"))
                .containsExactlyInAnyOrder(control.toString(), structure.toString());

        // ---- the control office reads the plan and approves, with no account ---------------------
        Client controlOffice = new Client();
        HttpResponse<byte[]> page = controlOffice.get("/r/" + controlTokenA);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat((String) read(page, "$.documentTitle")).isEqualTo("Plan de ferraillage des poutres");
        // The page only counts as opened once a person acts on it.
        assertThat(controlOffice.postJson("/r/" + controlTokenA + "/seen", "{}").statusCode()).isEqualTo(204);
        assertThat(controlOffice.get("/r/" + controlTokenA + "/document").body()).isEqualTo(TestFixtures.PDF);
        assertThat(controlOffice.postJson("/r/" + controlTokenA + "/opinion", answer("VSO", "")).statusCode())
                .isEqualTo(200);

        // ---- the structure office refuses ------------------------------------------------------------
        Client structureOffice = new Client();
        assertThat(structureOffice.get("/r/" + structureTokenA).statusCode()).isEqualTo(200);
        assertThat(structureOffice.postJson("/r/" + structureTokenA + "/opinion",
                answer("NC", "reprendre le ferraillage")).statusCode()).isEqualTo(200);

        // ---- the contractor sees: to be revised --------------------------------------------------
        HttpResponse<byte[]> dashboardAfterRefusal =
                khawla.get("/api/projects/%s/dashboard".formatted(project));
        assertThat((String) read(dashboardAfterRefusal, "$.documents[0].status")).isEqualTo("TO_REVISE");
        assertThat(JsonPath.<List<String>>read(text(dashboardAfterRefusal),
                "$.documents[0].reviewers[*].statusCode")).containsExactlyInAnyOrder("VSO", "NC");

        // ---- she corrects and sends revision B ---------------------------------------------------
        HttpResponse<byte[]> sentB = khawla.upload(
                "/api/projects/%s/documents/%s/revisions".formatted(project, document), "B", TestFixtures.PDF,
                List.of(control, structure));
        assertThat(sentB.statusCode()).isEqualTo(201);
        UUID revisionB = id(sentB, "$.revision.id");
        String controlTokenB = tokenFor(sentB, control);
        String structureTokenB = tokenFor(sentB, structure);
        everyTokenIssued.addAll(List.of(controlTokenB, structureTokenB));

        // A link to the older revision still works, and says it concerns a replaced version.
        HttpResponse<byte[]> oldPage = controlOffice.get("/r/" + controlTokenA);
        assertThat((Boolean) read(oldPage, "$.revisionSuperseded")).isTrue();

        // ---- both answer on B: approved, with a reserve ------------------------------------------
        assertThat(controlOffice.postJson("/r/" + controlTokenB + "/opinion", answer("VSO", "")).statusCode())
                .isEqualTo(200);
        assertThat(structureOffice.postJson("/r/" + structureTokenB + "/opinion",
                answer("VAO", "fournir la fiche du produit de scellement")).statusCode()).isEqualTo(200);

        HttpResponse<byte[]> dashboard = khawla.get("/api/projects/%s/dashboard".formatted(project));
        assertThat((String) read(dashboard, "$.documents[0].status")).isEqualTo("APPROVED_WITH_RESERVES");
        assertThat((String) read(dashboard, "$.documents[0].revision.label")).isEqualTo("B");
        assertThat((Integer) read(dashboard, "$.documents[0].revisionCount")).isEqualTo(2);
        assertThat((Integer) read(dashboard, "$.totals.APPROVED_WITH_RESERVES")).isEqualTo(1);

        // ---- an answer received by phone is entered on behalf, and the proof says so -------------
        assertThat(khawla.postJson("/api/projects/%s/revisions/%s/opinions".formatted(project, revisionB),
                "{\"reviewerId\":\"%s\",\"statusCode\":\"VSO\",\"comment\":\"par téléphone\"}"
                        .formatted(architect)).statusCode()).isEqualTo(422);
        HttpResponse<byte[]> review = khawla.get("/api/projects/%s/revisions/%s/review".formatted(project, revisionB));
        assertThat((String) read(review, "$.status")).isEqualTo("APPROVED_WITH_RESERVES");

        // ---- the journal tells the whole story, and contains no secret --------------------------
        HttpResponse<byte[]> history = khawla.get("/api/projects/%s/revisions/%s/history".formatted(project, revisionA));
        List<String> types = JsonPath.read(text(history), "$[*].type");
        assertThat(types).startsWith("RevisionSubmitted", "ReviewOpened")
                .contains("ReviewLinkIssued", "ReviewLinkOpened", "DocumentViewed", "OpinionGiven");
        for (String token : everyTokenIssued) {
            assertThat(text(history)).doesNotContain(token);
            assertThat(text(dashboard)).doesNotContain(token);
            assertThat(text(review)).doesNotContain(token);
        }

        // ---- someone who is not a member learns nothing ------------------------------------------
        Client outsider = new Client();
        outsider.signIn(outsiderEmail);
        HttpResponse<byte[]> hidden = outsider.get("/api/projects/%s/dashboard".formatted(project));
        HttpResponse<byte[]> absent = outsider.get("/api/projects/%s/dashboard".formatted(UUID.randomUUID()));
        assertThat(hidden.statusCode()).isEqualTo(404);
        assertThat(text(hidden)).isEqualTo(text(absent));
        assertThat(outsider.get("/api/projects/%s/revisions/%s/file".formatted(project, revisionA)).statusCode())
                .isEqualTo(404);
        assertThat(JsonPath.<List<?>>read(text(outsider.get("/api/projects")), "$")).isEmpty();

        // ---- signing out ends the session --------------------------------------------------------
        assertThat(khawla.post("/api/auth/logout").statusCode()).isEqualTo(204);
        assertThat(khawla.get("/api/projects").statusCode()).isEqualTo(401);
    }
}
