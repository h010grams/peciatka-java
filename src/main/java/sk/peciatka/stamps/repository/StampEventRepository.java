package sk.peciatka.stamps.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sk.peciatka.stamps.model.StampEvent;

public interface StampEventRepository extends JpaRepository<StampEvent, UUID> {

    /** Stamps of a card that haven't been undone, newest first. */
    @Query("""
            select e from StampEvent e
            where e.card.id = :cardId
              and e.type = sk.peciatka.stamps.model.StampEvent.Type.ADD
              and not exists (select r from StampEvent r where r.revokesEventId = e.id)
            order by e.seq desc
            """)
    List<StampEvent> findActiveStamps(@Param("cardId") UUID cardId, Limit limit);

    default Optional<StampEvent> findLastActiveStamp(UUID cardId) {
        return findActiveStamps(cardId, Limit.of(1)).stream().findFirst();
    }

    long countByCardId(UUID cardId);
}
