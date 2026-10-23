package sk.peciatka.stamps.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import sk.peciatka.stamps.domain.RewardStatus;
import sk.peciatka.stamps.model.Reward;

public interface RewardRepository extends JpaRepository<Reward, UUID> {

    Optional<Reward> findByEarnedByEventId(UUID earnedByEventId);

    /** The oldest unused reward is redeemed first. */
    Optional<Reward> findFirstByCardIdAndStatusOrderByEarnedAtAsc(UUID cardId, RewardStatus status);
}
