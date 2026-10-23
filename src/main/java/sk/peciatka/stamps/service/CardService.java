package sk.peciatka.stamps.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.peciatka.stamps.domain.DomainException;
import sk.peciatka.stamps.domain.RewardStatus;
import sk.peciatka.stamps.domain.StampingRules;
import sk.peciatka.stamps.model.Card;
import sk.peciatka.stamps.model.Program;
import sk.peciatka.stamps.model.Reward;
import sk.peciatka.stamps.model.StampEvent;
import sk.peciatka.stamps.repository.CardRepository;
import sk.peciatka.stamps.repository.ProgramRepository;
import sk.peciatka.stamps.repository.RewardRepository;
import sk.peciatka.stamps.repository.StampEventRepository;

/**
 * Use cases for the counter: register a client, add a stamp, redeem a reward, undo a mistaken stamp.
 * Every write runs in a transaction that first locks the card row.
 */
@Service
@Transactional
public class CardService {

    public record CardView(
            UUID id,
            String customerName,
            String phone,
            int stampCount,
            int stampsRequired,
            int rewardsAvailable,
            String rewardText,
            int totalVisits,
            Instant lastStampAt,
            Instant nextStampAllowedAt,
            boolean canRevoke) {
    }

    public record Registered(CardView card, boolean created) {
    }

    public record StampOutcome(CardView card, boolean rewardEarned) {
    }

    private final ProgramRepository programs;
    private final CardRepository cards;
    private final StampEventRepository events;
    private final RewardRepository rewards;
    private final Clock clock;

    public CardService(ProgramRepository programs, CardRepository cards, StampEventRepository events,
            RewardRepository rewards, Clock clock) {
        this.programs = programs;
        this.cards = cards;
        this.events = events;
        this.rewards = rewards;
        this.clock = clock;
    }

    public Program createProgram(String name, int stampsRequired, String rewardText, int cooldownHours) {
        return programs.save(new Program(name, stampsRequired, rewardText, cooldownHours));
    }

    /** Same phone → the existing card is returned instead of creating a duplicate. */
    public Registered register(UUID programId, String customerName, String phone) {
        String normalized = phone.replaceAll("[\\s-]", "");
        var existing = cards.findByPhone(normalized);
        if (existing.isPresent()) {
            return new Registered(toView(existing.get()), false);
        }
        Program program = programs.findById(programId).orElseThrow(() -> new NotFoundException("program"));
        Card card = cards.save(new Card(program, customerName.trim(), normalized, clock.instant()));
        return new Registered(toView(card), true);
    }

    @Transactional(readOnly = true)
    public CardView get(UUID cardId) {
        return toView(cards.findWithProgram(cardId).orElseThrow(() -> new NotFoundException("card")));
    }

    public StampOutcome addStamp(UUID cardId) {
        Instant now = clock.instant();
        Card card = lock(cardId);
        Program program = card.getProgram();

        StampingRules.nextStampAllowedAt(card.getLastStampAt(), program.getCooldownHours(), now)
                .ifPresent(retryAt -> {
                    throw new DomainException("cooldown", Map.of("retryAt", retryAt.toString()));
                });

        var result = StampingRules.applyStamp(card.counters(), program.getStampsRequired());
        StampEvent event = events.save(StampEvent.add(card, now));
        if (result.rewardEarned()) {
            rewards.save(new Reward(card, event.getId(), now));
        }
        card.apply(result.counters());
        card.setLastStampAt(now);
        return new StampOutcome(toView(card), result.rewardEarned());
    }

    public CardView redeem(UUID cardId) {
        Instant now = clock.instant();
        Card card = lock(cardId);
        card.apply(StampingRules.applyRedeem(card.counters()));
        Reward reward = rewards.findFirstByCardIdAndStatusOrderByEarnedAtAsc(cardId, RewardStatus.AVAILABLE)
                .orElseThrow(() -> new DomainException("no_reward"));
        reward.redeem(now);
        return toView(card);
    }

    public CardView revokeLastStamp(UUID cardId) {
        Instant now = clock.instant();
        Card card = lock(cardId);
        StampEvent last = events.findLastActiveStamp(cardId)
                .orElseThrow(() -> new DomainException("nothing_to_revoke"));
        if (!StampingRules.canRevoke(last.getCreatedAt(), now)) {
            throw new DomainException("revoke_expired");
        }

        Reward earned = rewards.findByEarnedByEventId(last.getId()).orElse(null);
        var counters = StampingRules.applyRevoke(card.counters(), card.getProgram().getStampsRequired(),
                earned == null ? null : earned.getStatus());
        if (earned != null && earned.getStatus() == RewardStatus.AVAILABLE) {
            earned.voidIt(now);
        }

        events.save(StampEvent.revoke(card, last, now));
        events.flush();
        card.apply(counters);
        card.setLastStampAt(events.findLastActiveStamp(cardId).map(StampEvent::getCreatedAt).orElse(null));
        return toView(card);
    }

    private Card lock(UUID cardId) {
        return cards.findForUpdate(cardId).orElseThrow(() -> new NotFoundException("card"));
    }

    private CardView toView(Card card) {
        Instant now = clock.instant();
        Program p = card.getProgram();
        boolean canRevoke = card.getId() != null && events.findLastActiveStamp(card.getId())
                .filter(e -> StampingRules.canRevoke(e.getCreatedAt(), now))
                .filter(e -> rewards.findByEarnedByEventId(e.getId())
                        .map(r -> r.getStatus() != RewardStatus.REDEEMED).orElse(true))
                .isPresent();
        return new CardView(
                card.getId(),
                card.getCustomerName(),
                card.getPhone(),
                card.getStampCount(),
                p.getStampsRequired(),
                card.getRewardsAvailable(),
                p.getRewardText(),
                card.getTotalVisits(),
                card.getLastStampAt(),
                StampingRules.nextStampAllowedAt(card.getLastStampAt(), p.getCooldownHours(), now).orElse(null),
                canRevoke);
    }
}
