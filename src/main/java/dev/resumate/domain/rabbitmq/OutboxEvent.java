package dev.resumate.domain.rabbitmq;

import dev.resumate.domain.common.BaseTimeEntity;
import dev.resumate.domain.enums.AggregateType;
import dev.resumate.domain.enums.EventStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 트랜잭셔널 아웃박스 테이블 - 메시지 발행 성공/실패 상태를 저장
 */
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long aggregateId;  //이벤트가 발생한 관련 엔티티id

    @Enumerated(EnumType.STRING)
    private AggregateType aggregateType;  //엔티티 종류

    @Enumerated(EnumType.STRING)
    private EventStatus status;  //메시지 발행 상태

    @Lob  //Large object -> text 타입
    private String payload;  //메시지 데이터

    private LocalDateTime publishedAt;

    public void markPublished() {
        this.status = EventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = EventStatus.FAILED;
    }
}
