package dev.resumate.common.rabbitmq;

import dev.resumate.domain.rabbitmq.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPollingScheduler {
    private final OutboxPollingService outboxPollingService;
    private final MessagePublisher messagePublisher;

    @Scheduled(fixedDelay = 1000) // 1초
    public void poll() {
        log.info("polling outbox events");
        publishPendingEventsWithSkipLocked();
    }

    //동시성 문제 해결
    public void publishPendingEventsWithSkipLocked() {
        List<OutboxEvent> events = outboxPollingService.lockAndMarkProcessing();  //트랜잭션A -> 커밋하면 processing으로 상태 변환
        for (OutboxEvent event : events) {
            // MQ + 상태 변경
            //여기서 서버가 중단되면 메시지 발행 안되고, processing으로 남아있음 -> 재처리 스케줄러가 다시 메시지 발행
            try {
                log.info("publishing outbox event: {}", event.getAggregateId());
                messagePublisher.publish(event); // MQ 발행을 트랜잭션 밖으로 분리
                //메시지 발행 성공 후, db 상태 변경 실패 -> 이때도 processing상태인데, 서버 중단일 때랑 구별 안되니까 재처리 스케줄러가 다시 메시지 발행(중복 발행은 컨슈머가 제어)
                messagePublisher.markPublished(event);  //트랜잭션B
            } catch (Exception e) {
                messagePublisher.markFailed(event);  //트랜잭션C
                //failed 상태도 재처리 스케줄러가 처리
            }
        }
    }
}
