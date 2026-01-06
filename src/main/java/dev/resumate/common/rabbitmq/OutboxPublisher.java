package dev.resumate.common.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.resumate.domain.rabbitmq.OutboxEvent;
import dev.resumate.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {
    private static final int BATCH_SIZE = 50;
    @Value("${rabbitmq.embedding.exchange}")
    private String exchange;
    @Value("${rabbitmq.embedding.routing-key}")
    private String routingKey;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Counter outboxDuplicateAttemptCounter;

    //커밋 직후 발행
    /*@Transactional
    public void publishPendingEventsAfterCommit(OutboxEvent event) {
        try {
            publish(event);
            event.markPublished();
        } catch (Exception e) {
            log.error("Outbox publish failed. id={}", event.getId(), e);
            event.markFailed();
        }
    }*/

    //주기적인 발행
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events =
                outboxEventRepository.findPendingEvents(
                        PageRequest.of(0, BATCH_SIZE)  //PENDING 상태인 레코드가 줄어들기 때문에 0 넣어도 됨
                );

        for (OutboxEvent event : events) {
            try {
                Thread.sleep(50); //동시성 문제 포착용
            } catch (InterruptedException ignored) {}
            int updated = outboxEventRepository.markPublishedIfPending(event.getId());

            // 이미 다른 인스턴스가 처리했음
            if (updated == 0) {
                outboxDuplicateAttemptCounter.increment();
            }

            try {
                log.info("publishing outbox event: {}", event.getAggregateId());
                publish(event); // MQ 호출
                //event.markPublished();
            } catch (Exception e) {
                event.markFailed();
            }
        }
    }

    //메시지 발행
    private void publish(OutboxEvent event) throws JsonProcessingException {
        rabbitTemplate.convertAndSend(
                exchange,
                routingKey,
                objectMapper.readValue(event.getPayload(), EmbeddingMessageDto.class)
        );
    }
}
