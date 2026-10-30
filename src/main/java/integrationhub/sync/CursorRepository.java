package integrationhub.sync;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CursorRepository extends JpaRepository<SyncCursor, String> {
}
