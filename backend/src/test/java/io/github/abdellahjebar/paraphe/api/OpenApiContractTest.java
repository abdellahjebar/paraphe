package io.github.abdellahjebar.paraphe.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The API contract is generated from the code and committed in {@code docs/api/openapi.json}.
 *
 * <p>This test fails whenever the generated contract differs from the committed one, so a change
 * to a route is always visible in the diff and reviewed on purpose. To accept a change:
 * {@code ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true}.
 */
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@Import(TestcontainersConfiguration.class)
class OpenApiContractTest {

    private static final Path COMMITTED = Path.of("..", "docs", "api", "openapi.json");

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private String generated() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode tree = mapper.readTree(body);
        // The server address depends on where the test runs; it is not part of the contract.
        ((ObjectNode) tree).remove("servers");
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
    }

    @Test
    void generatedContractMatchesTheCommittedOne() throws Exception {
        String generated = generated();

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(COMMITTED.getParent());
            Files.writeString(COMMITTED, generated + System.lineSeparator(), StandardCharsets.UTF_8);
            return;
        }

        assertThat(COMMITTED)
                .as("docs/api/openapi.json is missing: run with -Dopenapi.update=true to create it")
                .exists();
        JsonNode committed = mapper.readTree(Files.readString(COMMITTED, StandardCharsets.UTF_8));
        JsonNode current = mapper.readTree(generated);
        assertThat(current)
                .as("The API changed. Review the change, then regenerate with -Dopenapi.update=true")
                .isEqualTo(committed);
    }

    @Test
    void contractDescribesTheRealRoutesAndNoTestOnlyOnes() throws Exception {
        JsonNode paths = mapper.readTree(generated()).get("paths");

        assertThat(paths.has("/api/projects")).isTrue();
        assertThat(paths.has("/api/projects/{projectId}/dashboard")).isTrue();
        assertThat(paths.has("/api/projects/{projectId}/documents/{documentId}/revisions")).isTrue();
        assertThat(paths.has("/r/{token}")).isTrue();
        assertThat(paths.has("/r/{token}/opinion")).isTrue();
        paths.propertyNames().forEach(path -> assertThat(path).doesNotStartWith("/api/probe"));
    }
}
