package dev.portfolio.orderflow.inventory.dto;

import java.util.List;

public record InventoryReservationResponse(
        String status,
        List<Item> items
) {

    public record Item(
            Long productId,
            int reservedQuantity,
            int remainingAvailable,
            int totalReserved
    ) {
    }
}
