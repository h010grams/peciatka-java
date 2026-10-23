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

/** Append-only history: every stamp (ADD) and every undo (REVOKE, pointing to the ADD it cancels). */
@Entity
@Table(name = "stamp_events")
public class StampEvent {

    public enum Type {
        ADD,
        REVOKE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Card card;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(unique = true)
    private UUID revokesEventId;

    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Order within the card. Timestamps alone are not enough: two stamps can share the same
     * millisecond, and then "the last stamp" would be picked at random.
     */
    @Column(nullable = false)
    private long seq;

    protected StampEvent() {
    }

    private StampEvent(Card card, Type type, UUID revokesEventId, Instant createdAt) {
        this.card = card;
        this.type = type;
        this.revokesEventId = revokesEventId;
        this.createdAt = createdAt;
        this.seq = card.nextEventSeq();
    }

    public static StampEvent add(Card card, Instant now) {
        return new StampEvent(card, Type.ADD, null, now);
    }

    public static StampEvent revoke(Card card, StampEvent revoked, Instant now) {
        return new StampEvent(card, Type.REVOKE, revoked.getId(), now);
    }

    public long getSeq() {
        return seq;
    }

    public UUID getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public UUID getRevokesEventId() {
        return revokesEventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
