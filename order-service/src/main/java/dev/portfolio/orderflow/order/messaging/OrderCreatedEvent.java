package dev.portfolio.orderflow.order.messaging;

import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        List<Item> items
) {

    public OrderCreatedEvent {
        items = List.copyOf(items);
    }

    public record Item(
            Long productId,
            int quantity
    ) {
    }
}
