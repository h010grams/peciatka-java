package sk.peciatka.stamps.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import sk.peciatka.stamps.MutableClock;

/** End-to-end API tests: HTTP → controller → service → JPA → H2. */
@SpringBootTest
@Import(CardApiTest.TestClock.class)
class CardApiTest {

    static final Instant START = Instant.parse("2026-10-07T10:00:00Z");
    static final AtomicInteger PHONE = new AtomicInteger(100_000);

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    MutableClock clock;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        clock.set(START);
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createProgram(int stamps, int cooldownHours) throws Exception {
        String body = postJson("/api/programs", """
                {"name":"Loyalty card","stampsRequired":%d,"rewardText":"15%% off","cooldownHours":%d}
                """.formatted(stamps, cooldownHours))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String register(String programId) throws Exception {
        String phone = "+421905" + PHONE.incrementAndGet();
        String body = postJson("/api/cards", """
                {"programId":"%s","customerName":"Jana","phone":"%s"}
                """.formatted(programId, phone))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions stamp(String cardId) throws Exception {
        return mvc.perform(post("/api/cards/{id}/stamps", cardId));
    }

    @Test
    void stampUpdatesCountersAndStartsCooldown() throws Exception {
        String card = register(createProgram(10, 12));
        stamp(card)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardEarned").value(false))
                .andExpect(jsonPath("$.card.stampCount").value(1))
                .andExpect(jsonPath("$.card.totalVisits").value(1))
                .andExpect(jsonPath("$.card.nextStampAllowedAt").value(notNullValue()))
                .andExpect(jsonPath("$.card.canRevoke").value(true));
    }

    @Test
    void cooldownBlocksSecondStampUntilWindowPasses() throws Exception {
        String card = register(createProgram(10, 12));
        stamp(card).andExpect(status().isOk());

        stamp(card)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cooldown"))
                .andExpect(jsonPath("$.retryAt").value("2026-10-07T22:00:00Z"));

        clock.advance(Duration.ofHours(13));
        stamp(card).andExpect(status().isOk()).andExpect(jsonPath("$.card.stampCount").value(2));
    }

    @Test
    void nthStampEarnsRewardWhichCanBeRedeemedOnce() throws Exception {
        String card = register(createProgram(3, 0));
        stamp(card);
        stamp(card);
        stamp(card)
                .andExpect(jsonPath("$.rewardEarned").value(true))
                .andExpect(jsonPath("$.card.stampCount").value(0))
                .andExpect(jsonPath("$.card.rewardsAvailable").value(1));

        mvc.perform(post("/api/cards/{id}/rewards/redeem", card))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardsAvailable").value(0));

        mvc.perform(post("/api/cards/{id}/rewards/redeem", card))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("no_reward"));
    }

    @Test
    void revokingRewardStampVoidsTheReward() throws Exception {
        String card = register(createProgram(2, 0));
        stamp(card);
        stamp(card).andExpect(jsonPath("$.rewardEarned").value(true));

        mvc.perform(post("/api/cards/{id}/stamps/revoke-last", card))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stampCount").value(1))
                .andExpect(jsonPath("$.rewardsAvailable").value(0))
                .andExpect(jsonPath("$.totalVisits").value(1));
    }

    @Test
    void revokeReopensCooldown() throws Exception {
        String card = register(createProgram(10, 12));
        stamp(card);
        mvc.perform(post("/api/cards/{id}/stamps/revoke-last", card))
                .andExpect(jsonPath("$.stampCount").value(0))
                .andExpect(jsonPath("$.nextStampAllowedAt").value(nullValue()));
        stamp(card).andExpect(status().isOk());
    }

    @Test
    void cannotRevokeStampWhoseRewardWasRedeemed() throws Exception {
        String card = register(createProgram(2, 0));
        stamp(card);
        stamp(card);
        mvc.perform(post("/api/cards/{id}/rewards/redeem", card));

        mvc.perform(get("/api/cards/{id}", card)).andExpect(jsonPath("$.canRevoke").value(false));
        mvc.perform(post("/api/cards/{id}/stamps/revoke-last", card))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("reward_already_redeemed"));
    }

    @Test
    void cannotRevokeAfter24Hours() throws Exception {
        String card = register(createProgram(10, 12));
        stamp(card);
        clock.advance(Duration.ofHours(25));
        mvc.perform(post("/api/cards/{id}/stamps/revoke-last", card))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("revoke_expired"));
    }

    @Test
    void samePhoneReturnsTheExistingCard() throws Exception {
        String program = createProgram(10, 12);
        String first = postJson("/api/cards", """
                {"programId":"%s","customerName":"Jana","phone":"+421 905 777 888"}
                """.formatted(program)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = postJson("/api/cards", """
                {"programId":"%s","customerName":"Jana N.","phone":"+421905777888"}
                """.formatted(program)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
    }

    @Test
    void validationAndNotFound() throws Exception {
        postJson("/api/programs", """
                {"name":"","stampsRequired":1,"rewardText":"x","cooldownHours":500}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));

        mvc.perform(get("/api/cards/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("card_not_found"));

        mvc.perform(get("/api/cards/not-a-uuid")).andExpect(status().isNotFound());
    }
}
