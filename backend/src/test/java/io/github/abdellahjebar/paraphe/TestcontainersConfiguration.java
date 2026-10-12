package io.github.abdellahjebar.paraphe;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Same major version as compose.yaml, so tests run against what development uses. */
    private static final String POSTGRES_IMAGE = "postgres:17-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));
    }

}
