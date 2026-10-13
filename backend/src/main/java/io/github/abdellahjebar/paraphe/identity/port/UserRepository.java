package io.github.abdellahjebar.paraphe.identity.port;

import io.github.abdellahjebar.paraphe.identity.domain.AppUser;
import io.github.abdellahjebar.paraphe.identity.domain.UserId;
import java.time.Instant;
import java.util.Optional;

public interface UserRepository {

    void insert(AppUser user);

    /** The email must already be normalised. */
    Optional<AppUser> findByEmail(String normalizedEmail);

    Optional<AppUser> findById(UserId id);

    long count();

    void updatePassword(UserId id, String passwordHash, boolean mustChangePassword);

    void recordLogin(UserId id, Instant at);
}
