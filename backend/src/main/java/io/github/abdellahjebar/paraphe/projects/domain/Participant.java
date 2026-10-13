package io.github.abdellahjebar.paraphe.projects.domain;

import io.github.abdellahjebar.paraphe.reviews.domain.ReviewerId;
import java.util.Objects;

/**
 * A person or firm taking part in a project. Its identity is the {@link ReviewerId} the reviews
 * module uses, so a participant is the reviewer.
 */
public record Participant(
        ReviewerId id,
        ProjectId projectId,
        String name,
        ReviewerRole role,
        String specialty,
        String email,
        String phone,
        boolean active) {

    public Participant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("participant name must not be blank");
        }
        specialty = blankToNull(specialty);
        email = blankToNull(email);
        phone = blankToNull(phone);
        if (email == null && phone == null) {
            throw new IllegalArgumentException("a participant needs an email or a phone number");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
