package dev.resumate.common.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.resumate.domain.enums.AggregateType;
import dev.resumate.domain.enums.EventStatus;
import dev.resumate.domain.rabbitmq.OutboxEvent;
import dev.resumate.repository.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional  //테스트 종료 후 롤백 -> 단, 테스트하려는 메서드도 트랜잭션일 경우 롤백x
class OutboxPollingSchedulerTest {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollingSchedulerTest.class);
    @Autowired
    OutboxPollingScheduler outboxPollingScheduler;
    @Autowired
    OutboxPollingService outboxPollingService;
    @Autowired
    MessagePublisher messagePublisher;
    @Autowired
    OutboxEventRepository outboxEventRepository;
    @Autowired
    ObjectMapper objectMapper;
    private static final int THREAD_COUNT = 5;
    private static final int EVENT_COUNT = 100;

    //테스트 시작 전에 10개의 아웃박스 이벤트 저장
    @BeforeEach
    void setup() throws JsonProcessingException {
        for (int i = 0; i < EVENT_COUNT; i++) {
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateId(Long.valueOf(i))
                    .status(EventStatus.PENDING)
                    .payload(objectMapper.writeValueAsString(EmbeddingMessageDto.builder()
                            .memberId(1L)
                            .resumeId(1L)
                            .coverLetterDtoList(new ArrayList<>())
                            .build()))
                    .aggregateType(AggregateType.RESUME)
                    .build();
            outboxEventRepository.save(event);
        }
    }

    //테스트 종료 후 실행
    @AfterEach
    void cleanup() {

    }

    @Test
    @DisplayName("메시지 발행 - 트랜잭션 분리")
    void publishPendingEventsWithSkipLocked() {
        //given
        StopWatch sw = new StopWatch();
        sw.start("separated-transactions");

        //when
        outboxPollingScheduler.publishPendingEventsWithSkipLocked();
        sw.stop();

        //then
        System.out.println(sw.prettyPrint());
    }

    @Test
    @DisplayName("메시지 발행 - 트랜잭션 분리x")
    void notSeparatedTransactions() {
        //given
        StopWatch sw = new StopWatch();
        sw.start("not-separated-transactions");

        //when
        notSeparatedTransactionAndPublishPendingEventsWithSkipLocked();
        sw.stop();

        //then
        System.out.println(sw.prettyPrint());
    }

    @Test
    @DisplayName("트랜잭션 분리, 멀티 스레드")
    void concurrencyTest() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        long start = System.currentTimeMillis();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        // 다중 스레드로 publish 메서드 호출
        for (int i = 0; i < THREAD_COUNT; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                outboxPollingScheduler.publishPendingEventsWithSkipLocked();
            }, executor);

            futures.add(future);
        }
        // 모든 작업 완료 기다림
        CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        ).join();

        long end = System.currentTimeMillis();

        log.info("트랜잭션 분리 - 아웃박스 처리 총 소요시간: " + (end - start) + " ms");
        executor.shutdown();
    }

    @Test
    @DisplayName("트랜잭션 분리 x, 멀티 스레드")
    void concurrencyTest2() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        long start = System.currentTimeMillis();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        // 다중 스레드로 publish 메서드 호출
        for (int i = 0; i < THREAD_COUNT; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(this::notSeparatedTransactionAndPublishPendingEventsWithSkipLocked, executor);

            futures.add(future);
        }
        // 모든 작업 완료 기다림
        CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        ).join();

        long end = System.currentTimeMillis();

        log.info("하나의 트랜잭션 - 아웃박스 처리 총 소요시간: " + (end - start) + " ms");
        executor.shutdown();
    }

    //트랜잭션 합친 메서드
    @Transactional
    public void notSeparatedTransactionAndPublishPendingEventsWithSkipLocked() {
        List<OutboxEvent> events = outboxEventRepository.findPendingForUpdateSkipLocked(50);
        for (OutboxEvent event : events) {
            // MQ + 상태 변경
            try {
                //log.info("publishing outbox event: {}", event.getAggregateId());
                messagePublisher.publish(event); // MQ 발행을 트랜잭션 밖으로 분리
                messagePublisher.markPublished(event);  //트랜잭션B
            } catch (Exception e) {
                messagePublisher.markFailed(event);  //트랜잭션C
            }
        }
    }
}