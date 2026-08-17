package dev.resumate.common.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSender {

    @Value("${rabbitmq.embedding.exchange}")
    private String exchange;
    @Value("${rabbitmq.embedding.routing-key}")
    private String routingKey;

    private final RabbitTemplate rabbitTemplate;

    //메시지 발행
    //생산자 - 임베딩 처리하려는 자소서 질문에 대한 값으로 메시지 발행
    public void sendEmbeddingMessage(EmbeddingMessageDto message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        log.info("Message sent to {}: {}", exchange, routingKey);
    }
}
