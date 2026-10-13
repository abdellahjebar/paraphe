package io.github.abdellahjebar.paraphe.identity.infrastructure.security;

import java.security.Principal;
import java.util.UUID;

/** The signed-in person as the web layer sees them. It holds no password and no hash. */
public record AuthenticatedUser(UUID userId, String email, String displayName, boolean mustChangePassword)
        implements Principal {

    @Override
    public String getName() {
        return email;
    }
}
