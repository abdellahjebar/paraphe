package io.github.abdellahjebar.paraphe;

import io.github.abdellahjebar.paraphe.support.OwnerJdbc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * A real PostgreSQL, set up the way production is: the application connects as a restricted role
 * that owns nothing, and migrations run as the database owner.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Same major version as compose.yaml, so tests run against what development uses. */
    private static final String POSTGRES_IMAGE = "postgres:17-alpine";

    /** The role and its development password, as created by db/create-app-role.sql. */
    public static final String APP_ROLE = "paraphe_app";
    public static final String APP_PASSWORD = "paraphe_app_dev";

    @Bean
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                // The very script development and operations run, so tests cannot drift from it.
                .withCopyFileToContainer(MountableFile.forHostPath("db/create-app-role.sql"),
                        "/docker-entrypoint-initdb.d/01-create-app-role.sql")
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2));
    }

    @Bean
    DynamicPropertyRegistrar databaseProperties(PostgreSQLContainer container) {
        return registry -> {
            // The application: restricted role.
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", () -> APP_ROLE);
            registry.add("spring.datasource.password", () -> APP_PASSWORD);
            // Migrations: the owner.
            registry.add("spring.flyway.url", container::getJdbcUrl);
            registry.add("spring.flyway.user", container::getUsername);
            registry.add("spring.flyway.password", container::getPassword);
        };
    }

    @Bean
    OwnerJdbc ownerJdbc(PostgreSQLContainer container) {
        return new OwnerJdbc(JdbcClient.create(new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword())));
    }
}
