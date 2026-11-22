package dev.portfolio.orderflow.inventory;

import dev.portfolio.orderflow.inventory.domain.InventoryReservation;
import dev.portfolio.orderflow.inventory.domain.Product;
import dev.portfolio.orderflow.inventory.domain.ReservationStatus;

import dev.portfolio.orderflow.inventory.messaging.InventoryResultEvent;
import dev.portfolio.orderflow.inventory.messaging.OrderCreatedEvent;

import dev.portfolio.orderflow.inventory.repository.InventoryReservationRepository;
import dev.portfolio.orderflow.inventory.repository.ProductRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OrderReservationService {

    private static final Logger log =
            LoggerFactory.getLogger(OrderReservationService.class);

    private final InventoryReservationRepository reservationRepository;
    private final ProductRepository productRepository;

    public OrderReservationService(
            InventoryReservationRepository reservationRepository,
            ProductRepository productRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public InventoryResultEvent process(OrderCreatedEvent event) {

        validate(event);

        // Reserve each order ID only once.
        // PostgreSQL handles concurrent INSERT conflicts.

        int inserted = reservationRepository.beginIfAbsent(
                event.orderId()
        );

        InventoryReservation reservation =
                reservationRepository.findById(event.orderId())
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Reservation record not found"
                                )
                        );

        if (inserted == 0) {

            if (reservation.getStatus()
                    == ReservationStatus.PROCESSING) {

                throw new IllegalStateException(
                        "Reservation is still processing: "
                                + event.orderId()
                );
            }

            log.info(
                    "Order {} was already processed: {}",
                    event.orderId(),
                    reservation.getStatus()
            );

            return toEvent(reservation);
        }

        // Lock product rows in a consistent order.

        List<OrderCreatedEvent.Item> items =
                event.items()
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        OrderCreatedEvent.Item::productId
                                )
                        )
                        .toList();

        List<Product> products = new ArrayList<>();

        // First check ALL items.
        // Do not modify stock until every item passes.

        for (OrderCreatedEvent.Item item : items) {

            Product product = productRepository
                    .findByIdForUpdate(item.productId())
                    .orElse(null);

            if (product == null) {

                return reject(
                        reservation,
                        "Product not found: " + item.productId()
                );
            }

            if (product.getAvailableQuantity()
                    < item.quantity()) {

                return reject(
                        reservation,
                        "Insufficient stock for product: "
                                + product.getId()
                );
            }

            products.add(product);
        }

        // All products exist and have sufficient stock.

        for (int i = 0; i < items.size(); i++) {

            Product product = products.get(i);
            OrderCreatedEvent.Item item = items.get(i);

            product.reserve(item.quantity());
        }

        reservation.markReserved();

        log.info(
                "Reserved inventory for order {}",
                event.orderId()
        );

        return toEvent(reservation);
    }

    private InventoryResultEvent reject(
            InventoryReservation reservation,
            String reason
    ) {

        reservation.markRejected(reason);

        log.info(
                "Rejected inventory for order {}: {}",
                reservation.getOrderId(),
                reason
        );

        return toEvent(reservation);
    }

    private InventoryResultEvent toEvent(
            InventoryReservation reservation
    ) {

        return new InventoryResultEvent(
                reservation.getOrderId(),
                reservation.getStatus().name(),
                reservation.getReason()
        );
    }

    private void validate(OrderCreatedEvent event) {

        if (event == null
                || event.orderId() == null
                || event.orderId() <= 0) {

            throw new IllegalArgumentException(
                    "Order ID must be positive"
            );
        }

        if (event.items() == null
                || event.items().isEmpty()
                || event.items().size() > 50) {

            throw new IllegalArgumentException(
                    "Order must contain between 1 and 50 items"
            );
        }

        Set<Long> productIds = new HashSet<>();

        for (OrderCreatedEvent.Item item : event.items()) {

            if (item == null
                    || item.productId() == null
                    || item.productId() <= 0
                    || item.quantity() == null
                    || item.quantity() <= 0) {

                throw new IllegalArgumentException(
                        "Invalid order item"
                );
            }

            if (!productIds.add(item.productId())) {

                throw new IllegalArgumentException(
                        "Duplicate product in order: "
                                + item.productId()
                );
            }
        }
    }
}
