package sk.peciatka.stamps.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import sk.peciatka.stamps.domain.RewardStatus;

/** A reward earned by a specific stamp. Voided if that stamp is undone before the reward is used. */
@Entity
@Table(name = "rewards")
public class Reward {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Card card;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RewardStatus status = RewardStatus.AVAILABLE;

    @Column(nullable = false, unique = true)
    private UUID earnedByEventId;

    @Column(nullable = false)
    private Instant earnedAt;

    private Instant redeemedAt;
    private Instant voidedAt;

    protected Reward() {
    }

    public Reward(Card card, UUID earnedByEventId, Instant earnedAt) {
        this.card = card;
        this.earnedByEventId = earnedByEventId;
        this.earnedAt = earnedAt;
    }

    public void redeem(Instant now) {
        this.status = RewardStatus.REDEEMED;
        this.redeemedAt = now;
    }

    public void voidIt(Instant now) {
        this.status = RewardStatus.VOID;
        this.voidedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public RewardStatus getStatus() {
        return status;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }
}
