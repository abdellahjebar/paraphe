package io.github.abdellahjebar.paraphe.projects.domain;

import io.github.abdellahjebar.paraphe.reviews.domain.OpinionEffect;
import io.github.abdellahjebar.paraphe.reviews.domain.OpinionStatus;
import java.util.List;

/**
 * Statuses given to every new project, taken from observed practice and still to be confirmed with
 * the end user. They are only a starting point: a project can add, rename or deactivate statuses.
 */
public final class DefaultStatuses {

    private DefaultStatuses() {
    }

    public static List<OpinionStatus> all() {
        return List.of(
                new OpinionStatus("VSO", "Visé sans observation", OpinionEffect.APPROVING),
                new OpinionStatus("VAO", "Visé avec observations", OpinionEffect.APPROVING_WITH_RESERVES),
                new OpinionStatus("VPI", "Visé pour information", OpinionEffect.INFORMATIVE),
                new OpinionStatus("NC", "Non conforme", OpinionEffect.BLOCKING),
                new OpinionStatus("REF", "Refusé", OpinionEffect.BLOCKING));
    }
}
