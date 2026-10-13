package io.github.abdellahjebar.paraphe.identity.application;

import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.domain.JsonPayload;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.identity.domain.AppUser;
import io.github.abdellahjebar.paraphe.identity.domain.PasswordPolicy;
import io.github.abdellahjebar.paraphe.identity.domain.UserId;
import io.github.abdellahjebar.paraphe.identity.port.PasswordHasher;
import io.github.abdellahjebar.paraphe.identity.port.UserRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating accounts and changing passwords. There is no public sign-up. */
@Service
@Transactional
public class UserAdminService {

    private static final String TARGET_TYPE = "USER";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final AuditLog auditLog;
    private final Clock clock;

    UserAdminService(UserRepository users, PasswordHasher hasher, AuditLog auditLog, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * Creates an account. With {@code mustChangePassword} the person has to choose their own
     * password before doing anything else, so the one an administrator typed is only a first key.
     */
    public AppUser createUser(String email, String displayName, String rawPassword,
                              boolean mustChangePassword, String actor) {
        PasswordPolicy.check(rawPassword, email);
        String normalized = AppUser.normalizeEmail(email);
        if (users.findByEmail(normalized).isPresent()) {
            throw new EmailAlreadyUsedException();
        }
        AppUser user = new AppUser(UserId.random(), normalized, displayName, hasher.hash(rawPassword),
                true, mustChangePassword, clock.instant());
        try {
            users.insert(user);
        } catch (DuplicateKeyException e) {
            throw new EmailAlreadyUsedException();
        }
        auditLog.append(AuditEvent.of("UserCreated", actor, TARGET_TYPE, user.id().value(),
                JsonPayload.of("mustChangePassword", String.valueOf(mustChangePassword)), clock.instant()));
        return user;
    }

    /**
     * Creates the very first account, and only when there is none yet. Returns empty otherwise, so
     * leaving the setting in place on later starts is harmless.
     */
    public Optional<AppUser> createFirstUserIfNone(String email, String displayName, String rawPassword) {
        if (users.count() > 0) {
            return Optional.empty();
        }
        return Optional.of(createUser(email, displayName, rawPassword, false, "system:bootstrap"));
    }

    public void changePassword(UserId userId, String currentPassword, String newPassword) {
        AppUser user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (currentPassword == null || !hasher.matches(currentPassword, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        PasswordPolicy.check(newPassword, user.email());
        if (newPassword.equals(currentPassword)) {
            throw new io.github.abdellahjebar.paraphe.identity.domain.WeakPasswordException(
                    "The new password must be different from the current one");
        }
        users.updatePassword(userId, hasher.hash(newPassword), false);
        auditLog.append(AuditEvent.of("PasswordChanged", "user:" + userId.value(), TARGET_TYPE,
                userId.value(), JsonPayload.of(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> find(UserId id) {
        return users.findById(id);
    }
}
