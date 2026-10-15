package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import io.github.abdellahjebar.paraphe.identity.port.PasswordHasher;
import java.text.Normalizer;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id with the OWASP minimum parameters (19 MiB, 2 iterations, 1 lane). Passwords are normalised to
 * NFC first so the same text always gives the same hash. Hashes made earlier with bcrypt are still
 * accepted and are replaced by an Argon2id hash at the next successful sign-in.
 *
 * <p>A memory size below the OWASP minimum is refused at startup unless weak hashing is explicitly
 * allowed (tests only), so a leftover test setting cannot reach production.
 */
@Component
class Argon2idPasswordHasher implements PasswordHasher {

    static final int MINIMUM_MEMORY_KIB = 19456;
    private static final int ITERATIONS = 2;
    private static final int LANES = 1;
    private static final String PREFIX = "$argon2id$";

    private final Argon2PasswordEncoder argon2;
    private final BCryptPasswordEncoder legacy = new BCryptPasswordEncoder();
    private final int memoryKib;
    private final String throwawayHash;

    Argon2idPasswordHasher(@Value("${paraphe.security.argon2-memory-kib:19456}") int memoryKib,
                           @Value("${paraphe.security.allow-weak-hashing:false}") boolean allowWeak) {
        if (memoryKib < MINIMUM_MEMORY_KIB && !allowWeak) {
            throw new IllegalStateException("Argon2 memory %d KiB is below the minimum of %d"
                    .formatted(memoryKib, MINIMUM_MEMORY_KIB));
        }
        this.memoryKib = memoryKib;
        this.argon2 = new Argon2PasswordEncoder(16, 32, LANES, memoryKib, ITERATIONS);
        this.throwawayHash = argon2.encode(UUID.randomUUID().toString());
    }

    @Override
    public String hash(String rawPassword) {
        return argon2.encode(normalize(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        if (rawPassword == null || hash == null) {
            return false;
        }
        if (hash.startsWith("$2")) {
            return legacy.matches(rawPassword, hash);
        }
        return hash.startsWith(PREFIX) && argon2.matches(normalize(rawPassword), hash);
    }

    @Override
    public boolean needsRehash(String hash) {
        return hash == null || !hash.startsWith(PREFIX) || !hash.contains("$m=" + memoryKib + ",t=" + ITERATIONS + ",");
    }

    @Override
    public void burnTime(String rawPassword) {
        argon2.matches(normalize(rawPassword == null ? "" : rawPassword), throwawayHash);
    }

    private static String normalize(String password) {
        return Normalizer.normalize(password, Normalizer.Form.NFC);
    }
}
