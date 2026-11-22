package dev.portfolio.orderflow.inventory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.portfolio.orderflow.inventory.OrderReservationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class OrderCreatedListener {

    private static final Logger log =
            LoggerFactory.getLogger(OrderCreatedListener.class);

    private static final String INVENTORY_RESULT_TOPIC =
            "inventory-result";

    private final OrderReservationService orderReservationService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderCreatedListener(
            OrderReservationService orderReservationService,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.orderReservationService = orderReservationService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order-created")
    public void handle(String payload) throws Exception {

        OrderCreatedEvent event = objectMapper.readValue(
                payload,
                OrderCreatedEvent.class
        );

        log.info(
                "Received OrderCreated for order {}",
                event.orderId()
        );

        // This transaction commits before the Kafka result is sent.

        InventoryResultEvent result =
                orderReservationService.process(event);

        String resultPayload =
                objectMapper.writeValueAsString(result);

        kafkaTemplate.send(
                INVENTORY_RESULT_TOPIC,
                event.orderId().toString(),
                resultPayload
        ).get(10, TimeUnit.SECONDS);

        log.info(
                "Published InventoryResult for order {}: {}",
                event.orderId(),
                result.status()
        );
    }
}
