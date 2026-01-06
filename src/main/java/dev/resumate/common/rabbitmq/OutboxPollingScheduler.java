package dev.resumate.common.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPollingScheduler {
    private final OutboxPublisher outboxPublisher;

    @Scheduled(fixedDelay = 1000) // 1초
    public void poll() {
        log.info("polling outbox events");
        outboxPublisher.publishPendingEvents();
    }
}
