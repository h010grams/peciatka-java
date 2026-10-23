package sk.peciatka.stamps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import sk.peciatka.stamps.domain.DomainException;
import sk.peciatka.stamps.repository.StampEventRepository;
import sk.peciatka.stamps.service.CardService;

/** Four staff members scan the same card at the same moment → exactly one stamp. */
@SpringBootTest
class ConcurrentStampTest {

    @Autowired
    CardService service;

    @Autowired
    StampEventRepository events;

    @Test
    void simultaneousScansGiveExactlyOneStamp() throws Exception {
        UUID program = service.createProgram("Card", 10, "Free coffee", 12).getId();
        UUID card = service.register(program, "Petra", "+421905000111").card().id();

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    service.addStamp(card);
                    return true;
                } catch (DomainException e) {
                    assertThat(e.code()).isEqualTo("cooldown");
                    return false;
                }
            }));
        }
        start.countDown();

        int succeeded = 0;
        for (Future<Boolean> r : results) {
            if (r.get()) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(events.countByCardId(card)).isEqualTo(1);
        assertThat(service.get(card).stampCount()).isEqualTo(1);
    }
}
