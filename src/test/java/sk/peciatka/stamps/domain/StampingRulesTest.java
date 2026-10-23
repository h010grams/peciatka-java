package sk.peciatka.stamps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StampingRulesTest {

    private static final Instant T0 = Instant.parse("2026-10-07T10:00:00Z");

    private static Instant at(double hours) {
        return T0.plus(Duration.ofMinutes(Math.round(hours * 60)));
    }

    @Nested
    class Cooldown {
        @Test
        void firstStampIsAllowed() {
            assertThat(StampingRules.nextStampAllowedAt(null, 12, T0)).isEmpty();
        }

        @Test
        void blocksWithinWindowAndTellsWhenItOpens() {
            assertThat(StampingRules.nextStampAllowedAt(T0, 12, at(11.5))).contains(at(12));
        }

        @Test
        void allowsAtWindowEnd() {
            assertThat(StampingRules.nextStampAllowedAt(T0, 12, at(12))).isEmpty();
        }

        @Test
        void zeroDisablesTheLimit() {
            assertThat(StampingRules.nextStampAllowedAt(T0, 0, T0)).isEmpty();
        }
    }

    @Nested
    class Stamp {
        @Test
        void incrementsCounters() {
            var r = StampingRules.applyStamp(Counters.EMPTY, 10);
            assertThat(r.counters()).isEqualTo(new Counters(1, 0, 1));
            assertThat(r.rewardEarned()).isFalse();
        }

        @Test
        void nthStampEarnsRewardAndStartsNewCycle() {
            var r = StampingRules.applyStamp(new Counters(9, 0, 9), 10);
            assertThat(r.counters()).isEqualTo(new Counters(0, 1, 10));
            assertThat(r.rewardEarned()).isTrue();
        }

        @Test
        void rewardsAccumulate() {
            Counters c = Counters.EMPTY;
            int earned = 0;
            for (int i = 0; i < 25; i++) {
                var r = StampingRules.applyStamp(c, 10);
                c = r.counters();
                if (r.rewardEarned()) {
                    earned++;
                }
            }
            assertThat(earned).isEqualTo(2);
            assertThat(c).isEqualTo(new Counters(5, 2, 25));
        }
    }

    @Nested
    class Redeem {
        @Test
        void usesOneReward() {
            assertThat(StampingRules.applyRedeem(new Counters(3, 2, 23))).isEqualTo(new Counters(3, 1, 23));
        }

        @Test
        void failsWithoutReward() {
            assertThatThrownBy(() -> StampingRules.applyRedeem(new Counters(9, 0, 9)))
                    .isInstanceOf(DomainException.class)
                    .hasMessage("no_reward");
        }
    }

    @Nested
    class Revoke {
        @Test
        void removesRegularStamp() {
            assertThat(StampingRules.applyRevoke(new Counters(4, 0, 4), 10, null)).isEqualTo(new Counters(3, 0, 3));
        }

        @Test
        void revokingRewardStampVoidsRewardAndRestoresNMinusOne() {
            assertThat(StampingRules.applyRevoke(new Counters(0, 1, 10), 10, RewardStatus.AVAILABLE))
                    .isEqualTo(new Counters(9, 0, 9));
        }

        @Test
        void cannotRevokeWhenRewardWasRedeemed() {
            assertThatThrownBy(() -> StampingRules.applyRevoke(new Counters(0, 0, 10), 10, RewardStatus.REDEEMED))
                    .isInstanceOf(DomainException.class)
                    .hasMessage("reward_already_redeemed");
        }

        @Test
        void neverGoesNegative() {
            assertThat(StampingRules.applyRevoke(Counters.EMPTY, 10, null)).isEqualTo(Counters.EMPTY);
        }

        @Test
        void windowIs24Hours() {
            assertThat(StampingRules.canRevoke(T0, at(23.9))).isTrue();
            assertThat(StampingRules.canRevoke(T0, at(24.1))).isFalse();
        }
    }
}
