package sk.peciatka.stamps.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import sk.peciatka.stamps.model.Program;

public interface ProgramRepository extends JpaRepository<Program, UUID> {
}
