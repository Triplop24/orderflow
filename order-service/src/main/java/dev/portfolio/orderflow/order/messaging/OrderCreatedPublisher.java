package dev.portfolio.orderflow.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderCreatedPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(OrderCreatedPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderCreatedPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void publish(OrderCreatedEvent event) {

        String payload;

        try {
            payload = objectMapper.writeValueAsString(event);

        } catch (JsonProcessingException ex) {
            log.error(
                    "Failed to serialize OrderCreated event for order {}",
                    event.orderId(),
                    ex
            );
            return;
        }

        try {
            kafkaTemplate.send(
                    KafkaTopicConfig.ORDER_CREATED_TOPIC,
                    event.orderId().toString(),
                    payload
            ).whenComplete((result, error) -> {

                if (error != null) {
                    log.error(
                            "Failed to publish OrderCreated for order {}",
                            event.orderId(),
                            error
                    );
                    return;
                }

                log.info(
                        "Published OrderCreated for order {} to topic {}",
                        event.orderId(),
                        KafkaTopicConfig.ORDER_CREATED_TOPIC
                );
            });

        } catch (Exception ex) {
            log.error(
                    "Failed to initiate Kafka send for order {}",
                    event.orderId(),
                    ex
            );
        }
    }
}
