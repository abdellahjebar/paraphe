package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import io.github.abdellahjebar.paraphe.identity.port.PasswordHasher;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * bcrypt, cost 12 unless configured otherwise. A cost below 10 is refused at startup unless weak
 * hashing is explicitly allowed (tests only), so a leftover test setting cannot reach production.
 */
@Component
class BCryptPasswordHasher implements PasswordHasher {

    static final int MINIMUM_STRENGTH = 10;

    private final BCryptPasswordEncoder encoder;
    private final String throwawayHash;

    BCryptPasswordHasher(@Value("${paraphe.security.bcrypt-strength:12}") int strength,
                         @Value("${paraphe.security.allow-weak-hashing:false}") boolean allowWeak) {
        if (strength < MINIMUM_STRENGTH && !allowWeak) {
            throw new IllegalStateException("bcrypt strength %d is below the minimum of %d"
                    .formatted(strength, MINIMUM_STRENGTH));
        }
        this.encoder = new BCryptPasswordEncoder(strength);
        this.throwawayHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return rawPassword != null && hash != null && encoder.matches(rawPassword, hash);
    }

    @Override
    public void burnTime(String rawPassword) {
        encoder.matches(rawPassword == null ? "" : rawPassword, throwawayHash);
    }
}
