package sk.peciatka.stamps.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A salon's loyalty program: "N stamps → reward", at most one stamp per cooldown period. */
@Entity
@Table(name = "programs")
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int stampsRequired;

    @Column(nullable = false)
    private String rewardText;

    @Column(nullable = false)
    private int cooldownHours;

    protected Program() {
    }

    public Program(String name, int stampsRequired, String rewardText, int cooldownHours) {
        this.name = name;
        this.stampsRequired = stampsRequired;
        this.rewardText = rewardText;
        this.cooldownHours = cooldownHours;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getStampsRequired() {
        return stampsRequired;
    }

    public String getRewardText() {
        return rewardText;
    }

    public int getCooldownHours() {
        return cooldownHours;
    }
}
