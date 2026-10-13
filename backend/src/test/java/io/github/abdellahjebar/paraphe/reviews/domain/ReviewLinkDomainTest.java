package io.github.abdellahjebar.paraphe.reviews.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReviewLinkDomainTest {

    private static final Instant ISSUED = Instant.parse("2026-10-13T10:00:00Z");
    private static final Instant EXPIRES = ISSUED.plus(Duration.ofDays(30));

    private ReviewLink link(Instant firstOpened, Instant revoked) {
        return new ReviewLink(ReviewLinkId.random(), RevisionId.random(), ReviewerId.random(),
                ReviewToken.generate().hash(), ISSUED, EXPIRES, firstOpened, revoked);
    }

    @Test
    void tokenHas256BitsOfUrlSafeText() {
        ReviewToken token = ReviewToken.generate();

        assertThat(token.value()).hasSize(ReviewToken.LENGTH).matches("[A-Za-z0-9_-]+");
        assertThat(ReviewToken.hasTokenShape(token.value())).isTrue();
    }

    @Test
    void tokensDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(ReviewToken.generate().value());
        }

        assertThat(seen).hasSize(1000);
    }

    @Test
    void hashIsStableAndDifferentFromTheToken() {
        ReviewToken token = ReviewToken.generate();

        assertThat(token.hash()).isEqualTo(TokenHash.of(token.value()));
        assertThat(token.hash().hex()).isNotEqualTo(token.value()).hasSize(64);
    }

    @Test
    void tokenAndHashNeverPrintTheirValue() {
        ReviewToken token = ReviewToken.generate();

        assertThat(token.toString()).doesNotContain(token.value());
        assertThat(token.hash().toString()).doesNotContain(token.hash().hex());
    }

    @Test
    void shapeCheckRejectsAnythingThatIsNotAToken() {
        assertThat(ReviewToken.hasTokenShape(null)).isFalse();
        assertThat(ReviewToken.hasTokenShape("")).isFalse();
        assertThat(ReviewToken.hasTokenShape("short")).isFalse();
        assertThat(ReviewToken.hasTokenShape("../".repeat(20))).isFalse();
        assertThat(ReviewToken.hasTokenShape("a".repeat(44))).isFalse();
    }

    @Test
    void tokenHashRejectsNonHex() {
        assertThatThrownBy(() -> new TokenHash("not-a-hash")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linkIsUsableUntilTheExpiryInstant() {
        ReviewLink link = link(null, null);

        assertThat(link.isUsableAt(EXPIRES.minusSeconds(1))).isTrue();
        assertThat(link.isUsableAt(EXPIRES)).isFalse();
        assertThat(link.isUsableAt(EXPIRES.plusSeconds(1))).isFalse();
    }

    @Test
    void revokedLinkIsNeverUsable() {
        assertThat(link(null, ISSUED.plusSeconds(5)).isUsableAt(ISSUED.plusSeconds(10))).isFalse();
    }

    @Test
    void statusFollowsWhatHappenedToTheLink() {
        Instant now = ISSUED.plusSeconds(60);

        assertThat(link(null, null).statusAt(now, false)).isEqualTo(LinkStatus.ISSUED);
        assertThat(link(ISSUED.plusSeconds(5), null).statusAt(now, false)).isEqualTo(LinkStatus.OPENED);
        assertThat(link(ISSUED.plusSeconds(5), null).statusAt(now, true)).isEqualTo(LinkStatus.ANSWERED);
        assertThat(link(null, null).statusAt(EXPIRES.plusSeconds(1), false)).isEqualTo(LinkStatus.EXPIRED);
        assertThat(link(null, ISSUED.plusSeconds(5)).statusAt(now, false)).isEqualTo(LinkStatus.REVOKED);
    }

    @Test
    void revocationWinsOverExpiryAndOverAnAnswer() {
        ReviewLink revoked = link(ISSUED.plusSeconds(5), ISSUED.plusSeconds(10));

        assertThat(revoked.statusAt(EXPIRES.plusSeconds(1), true)).isEqualTo(LinkStatus.REVOKED);
    }

    @Test
    void linkMustExpireAfterItIsIssued() {
        assertThatThrownBy(() -> new ReviewLink(ReviewLinkId.random(), RevisionId.random(), ReviewerId.random(),
                ReviewToken.generate().hash(), ISSUED, ISSUED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
