package dev.portfolio.orderflow.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.portfolio.orderflow.order.domain.Order;
import dev.portfolio.orderflow.order.dto.CreateOrderRequest;
import dev.portfolio.orderflow.order.dto.OrderItemRequest;
import dev.portfolio.orderflow.order.dto.OrderResponse;
import dev.portfolio.orderflow.order.messaging.OrderCreatedEvent;
import dev.portfolio.orderflow.order.outbox.OutboxEvent;
import dev.portfolio.orderflow.order.outbox.OutboxEventRepository;
import dev.portfolio.orderflow.order.repository.OrderRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {

        Order order = new Order(request.customerEmail());

        Set<Long> productIds = new HashSet<>();

        for (OrderItemRequest item : request.items()) {

            if (!productIds.add(item.productId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate product in order: " + item.productId()
                );
            }

            order.addItem(
                    item.productId(),
                    item.quantity()
            );
        }

        Order saved = orderRepository.saveAndFlush(order);

        List<OrderCreatedEvent.Item> eventItems =
                saved.getItems()
                        .stream()
                        .map(item -> new OrderCreatedEvent.Item(
                                item.getProductId(),
                                item.getQuantity()
                        ))
                        .toList();

        OrderCreatedEvent event = new OrderCreatedEvent(
                saved.getId(),
                eventItems
        );

        String payload;

        try {
            payload = objectMapper.writeValueAsString(event);

        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Failed to serialize OrderCreated event",
                    ex
            );
        }

        OutboxEvent outboxEvent = new OutboxEvent(
                saved.getId(),
                "OrderCreated",
                payload
        );

        outboxEventRepository.save(outboxEvent);

        return OrderResponse.from(saved);
    }

    public OrderResponse getById(Long id) {

        Order order = orderRepository.findById(id)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order not found: " + id
                        )
                );

        return OrderResponse.from(order);
    }

    public List<OrderResponse> getAll() {

        return orderRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(OrderResponse::from)
                .toList();
    }
}
