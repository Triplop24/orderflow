package dev.portfolio.orderflow.order.messaging;

import dev.portfolio.orderflow.order.outbox.OutboxEvent;
import dev.portfolio.orderflow.order.outbox.OutboxEventRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OrderCreatedPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(OrderCreatedPublisher.class);

    private static final int BATCH_SIZE = 20;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderCreatedPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(
            fixedDelayString =
                    "${orderflow.outbox.poll-interval-ms:1000}"
    )
    @Transactional
    public void publishPending() {

        List<OutboxEvent> events =
                outboxEventRepository.findPendingForUpdate(
                        BATCH_SIZE
                );

        for (OutboxEvent event : events) {

            if (!"OrderCreated".equals(event.getEventType())) {
                throw new IllegalStateException(
                        "Unsupported outbox event type: "
                                + event.getEventType()
                );
            }

            try {
                kafkaTemplate.send(
                        KafkaTopicConfig.ORDER_CREATED_TOPIC,
                        event.getAggregateId().toString(),
                        event.getPayload()
                ).get(10, TimeUnit.SECONDS);

                event.markPublished();

                log.info(
                        "Published outbox event {} for order {}",
                        event.getId(),
                        event.getAggregateId()
                );

            } catch (InterruptedException ex) {

                Thread.currentThread().interrupt();

                throw new IllegalStateException(
                        "Interrupted while publishing outbox event "
                                + event.getId(),
                        ex
                );

            } catch (ExecutionException | TimeoutException ex) {

                throw new IllegalStateException(
                        "Failed to publish outbox event "
                                + event.getId(),
                        ex
                );
            }
        }
    }
}
