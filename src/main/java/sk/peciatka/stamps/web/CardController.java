package sk.peciatka.stamps.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sk.peciatka.stamps.model.Program;
import sk.peciatka.stamps.service.CardService;
import sk.peciatka.stamps.service.CardService.CardView;
import sk.peciatka.stamps.service.CardService.StampOutcome;

@RestController
@RequestMapping("/api")
public class CardController {

    public record CreateProgram(
            @NotBlank @Size(max = 60) String name,
            @Min(2) @Max(30) int stampsRequired,
            @NotBlank @Size(max = 60) String rewardText,
            @Min(0) @Max(168) int cooldownHours) {
    }

    public record ProgramView(UUID id, String name, int stampsRequired, String rewardText, int cooldownHours) {
        static ProgramView of(Program p) {
            return new ProgramView(p.getId(), p.getName(), p.getStampsRequired(), p.getRewardText(), p.getCooldownHours());
        }
    }

    public record RegisterCard(
            @NotNull UUID programId,
            @NotBlank @Size(max = 80) String customerName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9 \\-]{6,20}$", message = "invalid phone") String phone) {
    }

    private final CardService cards;

    public CardController(CardService cards) {
        this.cards = cards;
    }

    @PostMapping("/programs")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramView createProgram(@Valid @RequestBody CreateProgram body) {
        return ProgramView.of(cards.createProgram(body.name(), body.stampsRequired(), body.rewardText(), body.cooldownHours()));
    }

    /** 201 for a new card, 200 when a client with this phone already has one. */
    @PostMapping("/cards")
    public ResponseEntity<CardView> register(@Valid @RequestBody RegisterCard body) {
        var result = cards.register(body.programId(), body.customerName(), body.phone());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.card());
    }

    @GetMapping("/cards/{id}")
    public CardView get(@PathVariable UUID id) {
        return cards.get(id);
    }

    @PostMapping("/cards/{id}/stamps")
    public StampOutcome addStamp(@PathVariable UUID id) {
        return cards.addStamp(id);
    }

    @PostMapping("/cards/{id}/rewards/redeem")
    public CardView redeem(@PathVariable UUID id) {
        return cards.redeem(id);
    }

    @PostMapping("/cards/{id}/stamps/revoke-last")
    public CardView revokeLast(@PathVariable UUID id) {
        return cards.revokeLastStamp(id);
    }
}
