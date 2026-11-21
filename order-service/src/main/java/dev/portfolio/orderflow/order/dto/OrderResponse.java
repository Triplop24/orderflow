package dev.portfolio.orderflow.order.dto;

import dev.portfolio.orderflow.order.domain.Order;
import dev.portfolio.orderflow.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String customerEmail,
        OrderStatus status,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse from(Order order) {

        List<OrderItemResponse> items = order.getItems()
                .stream()
                .map(OrderItemResponse::from)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getCustomerEmail(),
                order.getStatus(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
