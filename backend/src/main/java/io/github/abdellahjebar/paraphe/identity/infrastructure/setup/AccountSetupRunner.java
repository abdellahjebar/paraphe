package io.github.abdellahjebar.paraphe.identity.infrastructure.setup;

import io.github.abdellahjebar.paraphe.identity.application.UserAdminService;
import io.github.abdellahjebar.paraphe.identity.domain.AppUser;
import io.github.abdellahjebar.paraphe.projects.application.ProjectAccess;
import io.github.abdellahjebar.paraphe.projects.domain.ProjectId;
import java.util.Arrays;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates accounts from configuration, since there is no sign-up.
 *
 * <ul>
 *   <li>{@code paraphe.bootstrap.email} / {@code .password}: the first account, created only while
 *       no account exists. Safe to leave in place.
 *   <li>{@code paraphe.admin.create-user.*}: one more account, which must choose its own password at
 *       first sign-in, optionally added to projects ({@code .projects} is a comma-separated list).
 * </ul>
 *
 * <p>Passwords are read, hashed and never printed.
 */
@Component
class AccountSetupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountSetupRunner.class);

    private final UserAdminService users;
    private final ProjectAccess access;
    private final String bootstrapEmail;
    private final String bootstrapPassword;
    private final String createEmail;
    private final String createPassword;
    private final String createName;
    private final String createProjects;

    AccountSetupRunner(UserAdminService users, ProjectAccess access,
                       @Value("${paraphe.bootstrap.email:}") String bootstrapEmail,
                       @Value("${paraphe.bootstrap.password:}") String bootstrapPassword,
                       @Value("${paraphe.admin.create-user.email:}") String createEmail,
                       @Value("${paraphe.admin.create-user.password:}") String createPassword,
                       @Value("${paraphe.admin.create-user.display-name:}") String createName,
                       @Value("${paraphe.admin.create-user.projects:}") String createProjects) {
        this.users = users;
        this.access = access;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
        this.createEmail = createEmail;
        this.createPassword = createPassword;
        this.createName = createName;
        this.createProjects = createProjects;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrapEmail.isBlank() && !bootstrapPassword.isBlank()) {
            users.createFirstUserIfNone(bootstrapEmail, displayNameFor(bootstrapEmail, ""), bootstrapPassword)
                    .ifPresent(user -> log.info("First account created for {}", user.email()));
        }
        if (!createEmail.isBlank() && !createPassword.isBlank()) {
            AppUser user = users.createUser(createEmail, displayNameFor(createEmail, createName),
                    createPassword, true, "system:admin-command");
            Arrays.stream(createProjects.split(","))
                    .map(String::strip)
                    .filter(id -> !id.isEmpty())
                    .forEach(id -> access.grant(new ProjectId(UUID.fromString(id)), user.id().value(),
                            "system:admin-command"));
            log.info("Account created for {}, it must change its password at first sign-in", user.email());
        }
    }

    private static String displayNameFor(String email, String requested) {
        if (!requested.isBlank()) {
            return requested;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
