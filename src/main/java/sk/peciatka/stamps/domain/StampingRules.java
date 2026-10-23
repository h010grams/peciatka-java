package sk.peciatka.stamps.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Stamp and reward rules as pure functions — no database, easy to unit test.
 *
 * <p>Model: the N-th stamp earns a reward and the counter starts a new cycle right away,
 * so rewards accumulate and the client redeems them whenever she wants.
 */
public final class StampingRules {

    /** How long after a stamp it can still be undone. */
    public static final Duration REVOKE_WINDOW = Duration.ofHours(24);

    private StampingRules() {
    }

    public record StampResult(Counters counters, boolean rewardEarned) {
    }

    /** When the next stamp is allowed; empty means "now". */
    public static Optional<Instant> nextStampAllowedAt(Instant lastStampAt, int cooldownHours, Instant now) {
        if (lastStampAt == null || cooldownHours <= 0) {
            return Optional.empty();
        }
        Instant allowed = lastStampAt.plus(Duration.ofHours(cooldownHours));
        return allowed.isAfter(now) ? Optional.of(allowed) : Optional.empty();
    }

    public static StampResult applyStamp(Counters c, int stampsRequired) {
        int count = c.stampCount() + 1;
        if (count >= stampsRequired) {
            return new StampResult(new Counters(0, c.rewardsAvailable() + 1, c.totalVisits() + 1), true);
        }
        return new StampResult(new Counters(count, c.rewardsAvailable(), c.totalVisits() + 1), false);
    }

    public static Counters applyRedeem(Counters c) {
        if (c.rewardsAvailable() <= 0) {
            throw new DomainException("no_reward");
        }
        return new Counters(c.stampCount(), c.rewardsAvailable() - 1, c.totalVisits());
    }

    public static boolean canRevoke(Instant stampedAt, Instant now) {
        return !now.isAfter(stampedAt.plus(REVOKE_WINDOW));
    }

    /**
     * Undo the last stamp. If that stamp earned a reward, the reward is voided and the counter goes back
     * to N-1. If the reward was already redeemed, the stamp can't be undone — the service was given.
     *
     * @param earnedReward status of the reward earned by this stamp, or {@code null} if it earned none
     */
    public static Counters applyRevoke(Counters c, int stampsRequired, RewardStatus earnedReward) {
        int totalVisits = Math.max(0, c.totalVisits() - 1);
        if (earnedReward != null && earnedReward != RewardStatus.VOID) {
            if (earnedReward == RewardStatus.REDEEMED) {
                throw new DomainException("reward_already_redeemed");
            }
            return new Counters(Math.max(0, stampsRequired - 1), Math.max(0, c.rewardsAvailable() - 1), totalVisits);
        }
        return new Counters(Math.max(0, c.stampCount() - 1), c.rewardsAvailable(), totalVisits);
    }
}
