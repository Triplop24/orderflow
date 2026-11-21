package dev.portfolio.orderflow.inventory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.portfolio.orderflow.inventory.InventoryService;
import dev.portfolio.orderflow.inventory.dto.ReserveInventoryRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OrderCreatedListener {

    private static final Logger log =
            LoggerFactory.getLogger(OrderCreatedListener.class);

    private static final String INVENTORY_RESULT_TOPIC =
            "inventory-result";

    private final InventoryService inventoryService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderCreatedListener(
            InventoryService inventoryService,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.inventoryService = inventoryService;
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

        List<ReserveInventoryRequest.Item> items =
                event.items()
                        .stream()
                        .map(item -> new ReserveInventoryRequest.Item(
                                item.productId(),
                                item.quantity()
                        ))
                        .toList();

        String status;
        String reason = null;

        try {
            inventoryService.reserve(
                    new ReserveInventoryRequest(items)
            );

            status = "RESERVED";

            log.info(
                    "Inventory reserved for order {}",
                    event.orderId()
            );

        } catch (ResponseStatusException exception) {

            status = "REJECTED";
            reason = exception.getReason();

            log.info(
                    "Inventory rejected for order {}: {}",
                    event.orderId(),
                    reason
            );
        }

        InventoryResultEvent result = new InventoryResultEvent(
                event.orderId(),
                status,
                reason
        );

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
                status
        );
    }
}
