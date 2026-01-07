package dev.resumate.common.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.resumate.domain.rabbitmq.OutboxEvent;
import dev.resumate.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class MessagePublisher {

    @Value("${rabbitmq.embedding.exchange}")
    private String exchange;
    @Value("${rabbitmq.embedding.routing-key}")
    private String routingKey;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    //메시지 발행 후 상태 변경
    /*@Transactional
    public void publishEvent(OutboxEvent event) {
        try {
            log.info("publishing outbox event: {}", event.getAggregateId());
            publish(event); // MQ 호출
            outboxEventRepository.markPublished(event.getId(), LocalDateTime.now());
        } catch (Exception e) {
            outboxEventRepository.markFailed(event.getId());
        }
    }*/

    //메시지 발행
    public void publish(OutboxEvent event) throws JsonProcessingException {
        rabbitTemplate.convertAndSend(
                exchange,
                routingKey,
                objectMapper.readValue(event.getPayload(), EmbeddingMessageDto.class)
        );
    }

    @Transactional
    public void markPublished(OutboxEvent event) {
        outboxEventRepository.markPublished(event.getId(), LocalDateTime.now());
    }

    @Transactional
    public void markFailed(OutboxEvent event) {
        outboxEventRepository.markFailed(event.getId());
    }
}
