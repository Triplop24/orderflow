package dev.portfolio.orderflow.order.dto;

import dev.portfolio.orderflow.order.domain.OrderItem;

public record OrderItemResponse(
        Long id,
        Long productId,
        int quantity
) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getQuantity()
        );
    }
}
