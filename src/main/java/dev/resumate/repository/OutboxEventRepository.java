package dev.resumate.repository;

import dev.resumate.domain.rabbitmq.OutboxEvent;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("""
        SELECT o FROM OutboxEvent o
        WHERE o.status = 'PENDING'
        ORDER BY o.createdAt
    """)
    List<OutboxEvent> findPendingEvents(Pageable pageable);

    //PROCESSING으로 상태 변경
    @Modifying(clearAutomatically = true, flushAutomatically = true)  //bulk update이므로 두 옵션을 켜야함
    @Query("""
    UPDATE OutboxEvent o
    SET o.status = 'PROCESSING'
    WHERE o.id = :id AND o.status = 'PENDING'
    """)
    int markProcessingIfPending(@Param("id") Long id);

    //이벤트 조회 - skip lock
    @Query(
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
    List<OutboxEvent> findPendingForUpdateSkipLocked(@Param("limit") int limit);

    //발행 성공
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    UPDATE OutboxEvent o
    SET o.status = 'PUBLISHED', o.publishedAt = :publishedAt
    WHERE o.id = :id
    """)
    void markPublished(@Param("id") Long id, @Param("publishedAt") LocalDateTime publishedAt);

    //발행 실패
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    UPDATE OutboxEvent o
    SET o.status = 'FAILED'
    WHERE o.id = :id
    """)
    void markFailed(@Param("id") Long id);
}
