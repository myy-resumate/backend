//package dev.resumate.common.rabbitmq;
//
//import dev.resumate.domain.rabbitmq.OutboxEvent;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.event.TransactionPhase;
//import org.springframework.transaction.event.TransactionalEventListener;
//
//@Component
//@RequiredArgsConstructor
//public class OutboxAfterCommitPublisher {
//    private final OutboxPublisher outboxPublisher;
//
//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
//    public void afterCommit(OutboxEvent event) {
//        outboxPublisher.publishPendingEventsAfterCommit(event);
//    }
//}
