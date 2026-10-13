package io.github.abdellahjebar.paraphe.shared.security;

import java.security.Principal;
import java.util.UUID;

/**
 * The signed-in person as the web layer sees them. It holds no password and no hash.
 * Lives in the shared kernel, which depends on nothing, so any module's controllers can use it
 * without creating a dependency cycle with the identity module.
 */
public record AuthenticatedUser(UUID userId, String email, String displayName, boolean mustChangePassword)
        implements Principal {

    @Override
    public String getName() {
        return email;
    }
}
