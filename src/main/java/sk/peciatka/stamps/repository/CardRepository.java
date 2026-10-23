package sk.peciatka.stamps.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sk.peciatka.stamps.model.Card;

public interface CardRepository extends JpaRepository<Card, UUID> {

    /**
     * SELECT … FOR UPDATE: locks the card row until the transaction ends, so two simultaneous
     * scans of the same card run one after another and the second one sees the first stamp.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Card c join fetch c.program where c.id = :id")
    Optional<Card> findForUpdate(@Param("id") UUID id);

    @Query("select c from Card c join fetch c.program where c.id = :id")
    Optional<Card> findWithProgram(@Param("id") UUID id);

    Optional<Card> findByPhone(String phone);
}
