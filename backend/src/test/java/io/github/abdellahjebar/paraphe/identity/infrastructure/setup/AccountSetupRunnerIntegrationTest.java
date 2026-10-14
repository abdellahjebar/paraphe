package io.github.abdellahjebar.paraphe.identity.infrastructure.setup;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.abdellahjebar.paraphe.TestcontainersConfiguration;
import io.github.abdellahjebar.paraphe.identity.application.AuthenticationResult;
import io.github.abdellahjebar.paraphe.identity.application.AuthenticationResult.Outcome;
import io.github.abdellahjebar.paraphe.identity.application.AuthenticationService;
import io.github.abdellahjebar.paraphe.projects.application.ProjectAccess;
import io.github.abdellahjebar.paraphe.projects.application.ProjectService;
import io.github.abdellahjebar.paraphe.projects.domain.Project;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Starts the whole application with account-creation settings, as an operator would, and checks
 * that the accounts exist and that no password is ever printed.
 */
@SpringBootTest(properties = {
        "paraphe.bootstrap.email=Admin@Example.org",
        "paraphe.bootstrap.password=first account passphrase",
        "paraphe.admin.create-user.email=khawla@example.org",
        "paraphe.admin.create-user.password=temporary passphrase 123",
        "paraphe.admin.create-user.display-name=Khawla"
})
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AccountSetupRunnerIntegrationTest {

    @Autowired
    private AuthenticationService authentication;

    @Autowired
    private ProjectService projects;

    @Autowired
    private ProjectAccess access;

    @Test
    void firstAccountIsCreatedFromConfigurationAndCanSignIn(CapturedOutput output) {
        AuthenticationResult result = authentication.authenticate("admin@example.org", "first account passphrase",
                "10.9.0.1");

        assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(result.user().mustChangePassword()).isFalse();
        assertThat(output.getAll()).doesNotContain("first account passphrase");
    }

    @Test
    void secondAccountMustChangeItsPasswordAndIsNotLoggedInClear(CapturedOutput output) {
        AuthenticationResult result = authentication.authenticate("khawla@example.org",
                "temporary passphrase 123", "10.9.0.2");

        assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(result.user().mustChangePassword()).isTrue();
        assertThat(result.user().displayName()).isEqualTo("Khawla");
        assertThat(output.getAll()).doesNotContain("temporary passphrase 123");
    }

    @Test
    void noDefaultPasswordIsPrintedAtStartup(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain("generated security password");
    }

    @Test
    void accountsCreatedAtStartupStartWithoutAnyProject() {
        AuthenticationResult result = authentication.authenticate("khawla@example.org",
                "temporary passphrase 123", "10.9.0.3");
        Project project = projects.createProject("Chantier isolé", "test");

        assertThat(access.isMember(project.id(), result.user().id().value())).isFalse();
    }
}
