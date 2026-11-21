package dev.portfolio.orderflow.order;

import dev.portfolio.orderflow.order.domain.Order;
import dev.portfolio.orderflow.order.domain.OrderStatus;
import dev.portfolio.orderflow.order.messaging.InventoryResultEvent;
import dev.portfolio.orderflow.order.repository.OrderRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderStatusService {

    private static final Logger log =
            LoggerFactory.getLogger(OrderStatusService.class);

    private final OrderRepository orderRepository;

    public OrderStatusService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void applyInventoryResult(
            InventoryResultEvent event
    ) {

        if (event == null
                || event.orderId() == null
                || event.orderId() <= 0) {

            throw new IllegalArgumentException(
                    "Inventory result must contain a valid order ID"
            );
        }

        OrderStatus targetStatus = switch (event.status()) {
            case "RESERVED" -> OrderStatus.CONFIRMED;
            case "REJECTED" -> OrderStatus.REJECTED;
            default -> throw new IllegalArgumentException(
                    "Unknown inventory result status: "
                            + event.status()
            );
        };

        Order order = orderRepository
                .findByIdForUpdate(event.orderId())
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Order not found: " + event.orderId()
                        )
                );

        // Kafka can deliver the same message more than once.
        // Applying the same result twice must not fail.

        if (order.getStatus() == targetStatus) {

            log.info(
                    "Order {} already has status {}, ignoring duplicate",
                    order.getId(),
                    targetStatus
            );

            return;
        }

        // An already completed order must not change
        // from CONFIRMED to REJECTED or vice versa.

        if (order.getStatus() != OrderStatus.PENDING) {

            log.warn(
                    "Ignoring conflicting inventory result for order {}: "
                            + "current={}, incoming={}",
                    order.getId(),
                    order.getStatus(),
                    targetStatus
            );

            return;
        }

        if (targetStatus == OrderStatus.CONFIRMED) {
            order.confirm();
        } else {
            order.reject();
        }

        log.info(
                "Order {} status changed to {}",
                order.getId(),
                order.getStatus()
        );
    }
}
