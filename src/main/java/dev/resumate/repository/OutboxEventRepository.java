package dev.resumate.repository;

import dev.resumate.domain.rabbitmq.OutboxEvent;
import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    //@Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o FROM OutboxEvent o
        WHERE o.status = 'PENDING'
        ORDER BY o.createdAt
    """)
    List<OutboxEvent> findPendingEvents(Pageable pageable);

    /*@Query(
            value = """
    SELECT *
    FROM outbox_event
    WHERE status = 'PENDING'
    ORDER BY created_at
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
  """,
            nativeQuery = true
    )
    List<OutboxEvent> findPendingForUpdateSkipLocked(@Param("limit") int limit, Pageable pageable);
*/
}
