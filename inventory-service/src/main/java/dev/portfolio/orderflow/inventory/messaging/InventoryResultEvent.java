package dev.portfolio.orderflow.inventory.messaging;

public record InventoryResultEvent(
        Long orderId,
        String status,
        String reason
) {
}
