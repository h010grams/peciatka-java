package sk.peciatka.stamps.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import sk.peciatka.stamps.domain.Counters;

/** A client's loyalty card. Counters are denormalized for fast reads; the full history is in StampEvent. */
@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Program program;

    @Column(nullable = false)
    private String customerName;

    /** Same phone → same card, so a client never ends up with duplicates. */
    @Column(nullable = false, unique = true)
    private String phone;

    private int stampCount;
    private int rewardsAvailable;
    private int totalVisits;
    private Instant lastStampAt;

    /** Counter for the card's events. Assigned under the row lock, so the order is never ambiguous. */
    private long eventSeq;

    @Column(nullable = false)
    private Instant createdAt;

    protected Card() {
    }

    public Card(Program program, String customerName, String phone, Instant now) {
        this.program = program;
        this.customerName = customerName;
        this.phone = phone;
        this.createdAt = now;
    }

    public Counters counters() {
        return new Counters(stampCount, rewardsAvailable, totalVisits);
    }

    public void apply(Counters c) {
        this.stampCount = c.stampCount();
        this.rewardsAvailable = c.rewardsAvailable();
        this.totalVisits = c.totalVisits();
    }

    public long nextEventSeq() {
        return ++eventSeq;
    }

    public void setLastStampAt(Instant lastStampAt) {
        this.lastStampAt = lastStampAt;
    }

    public UUID getId() {
        return id;
    }

    public Program getProgram() {
        return program;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getPhone() {
        return phone;
    }

    public int getStampCount() {
        return stampCount;
    }

    public int getRewardsAvailable() {
        return rewardsAvailable;
    }

    public int getTotalVisits() {
        return totalVisits;
    }

    public Instant getLastStampAt() {
        return lastStampAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
