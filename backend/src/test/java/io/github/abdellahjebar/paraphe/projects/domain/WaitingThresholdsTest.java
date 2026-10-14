package io.github.abdellahjebar.paraphe.projects.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WaitingThresholdsTest {

    @Test
    void waitingGrowsFromNoneToChaseToLate() {
        WaitingThresholds thresholds = WaitingThresholds.DEFAULT;

        assertThat(thresholds.attentionFor(0)).isEqualTo(Attention.NONE);
        assertThat(thresholds.attentionFor(7)).isEqualTo(Attention.NONE);
        assertThat(thresholds.attentionFor(8)).isEqualTo(Attention.CHASE);
        assertThat(thresholds.attentionFor(14)).isEqualTo(Attention.CHASE);
        assertThat(thresholds.attentionFor(15)).isEqualTo(Attention.LATE);
        assertThat(thresholds.attentionFor(400)).isEqualTo(Attention.LATE);
    }

    @Test
    void lateMustComeAfterChase() {
        assertThatThrownBy(() -> new WaitingThresholds(10, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WaitingThresholds(10, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thresholdsStayWithinReasonableBounds() {
        assertThatThrownBy(() -> new WaitingThresholds(0, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WaitingThresholds(5, 366)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new WaitingThresholds(1, 365).lateFromDays()).isEqualTo(365);
    }
}
