package dev.portfolio.orderflow.order.messaging;

public record InventoryResultEvent(
        Long orderId,
        String status,
        String reason
) {
}
