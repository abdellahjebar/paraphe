package io.github.abdellahjebar.paraphe.identity.application;

import io.github.abdellahjebar.paraphe.audit.domain.AuditEvent;
import io.github.abdellahjebar.paraphe.audit.domain.JsonPayload;
import io.github.abdellahjebar.paraphe.audit.port.AuditLog;
import io.github.abdellahjebar.paraphe.identity.domain.AppUser;
import io.github.abdellahjebar.paraphe.identity.port.LoginAttemptLimiter;
import io.github.abdellahjebar.paraphe.identity.port.PasswordHasher;
import io.github.abdellahjebar.paraphe.identity.port.UserRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks a sign-in attempt.
 *
 * <p>Failures never throw, so the journal entry written for them is kept. Both the account and the
 * caller's address are counted: guessing against one account from many addresses, or many accounts
 * from one address, hits a limit either way.
 */
@Service
@Transactional
public class AuthenticationService {

    private static final String TARGET_TYPE = "USER";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final LoginAttemptLimiter limiter;
    private final AuditLog auditLog;
    private final Clock clock;

    AuthenticationService(UserRepository users, PasswordHasher hasher, LoginAttemptLimiter limiter,
                          AuditLog auditLog, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.limiter = limiter;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * @param clientKey who is asking (typically the network address), used only for rate limiting
     */
    public AuthenticationResult authenticate(String email, String rawPassword, String clientKey) {
        String normalized = AppUser.normalizeEmail(email);
        String accountKey = "account:" + normalized;
        String clientLimitKey = "client:" + (clientKey == null ? "unknown" : clientKey);
        String password = rawPassword == null ? "" : rawPassword;

        if (limiter.isBlocked(accountKey) || limiter.isBlocked(clientLimitKey)) {
            return AuthenticationResult.locked();
        }

        Optional<AppUser> found = users.findByEmail(normalized).filter(AppUser::active);
        boolean valid;
        if (found.isPresent()) {
            valid = hasher.matches(password, found.get().passwordHash());
        } else {
            hasher.burnTime(password);
            valid = false;
        }

        if (!valid) {
            limiter.recordFailure(accountKey);
            limiter.recordFailure(clientLimitKey);
            found.ifPresent(user -> record("LoginFailed", user));
            return AuthenticationResult.invalid();
        }

        AppUser user = found.get();
        limiter.reset(accountKey);
        users.recordLogin(user.id(), clock.instant());
        if (hasher.needsRehash(user.passwordHash())) {
            // The raw password is only known here, so this is the one moment an old hash can be replaced.
            users.updatePassword(user.id(), hasher.hash(password), user.mustChangePassword());
            record("PasswordHashUpgraded", user);
        }
        record("LoginSucceeded", user);
        return AuthenticationResult.success(user);
    }

    private void record(String type, AppUser user) {
        auditLog.append(AuditEvent.of(type, "user:" + user.id().value(), TARGET_TYPE, user.id().value(),
                JsonPayload.of(), clock.instant()));
    }
}
