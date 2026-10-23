package sk.peciatka.stamps.domain;

/** Counters stored on a card: stamps in the current cycle, unused rewards and lifetime visits. */
public record Counters(int stampCount, int rewardsAvailable, int totalVisits) {

    public static final Counters EMPTY = new Counters(0, 0, 0);
}
