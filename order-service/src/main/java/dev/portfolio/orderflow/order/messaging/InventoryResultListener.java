package dev.portfolio.orderflow.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.portfolio.orderflow.order.OrderStatusService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryResultListener {

    private static final Logger log =
            LoggerFactory.getLogger(InventoryResultListener.class);

    private final OrderStatusService orderStatusService;
    private final ObjectMapper objectMapper;

    public InventoryResultListener(
            OrderStatusService orderStatusService,
            ObjectMapper objectMapper
    ) {
        this.orderStatusService = orderStatusService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory-result")
    public void handle(String payload) throws Exception {

        InventoryResultEvent event = objectMapper.readValue(
                payload,
                InventoryResultEvent.class
        );

        log.info(
                "Received InventoryResult for order {}: {}",
                event.orderId(),
                event.status()
        );

        orderStatusService.applyInventoryResult(event);
    }
}
