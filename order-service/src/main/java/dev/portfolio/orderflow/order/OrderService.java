package dev.portfolio.orderflow.order;

import dev.portfolio.orderflow.order.domain.Order;
import dev.portfolio.orderflow.order.dto.CreateOrderRequest;
import dev.portfolio.orderflow.order.dto.OrderItemRequest;
import dev.portfolio.orderflow.order.dto.OrderResponse;
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

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
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
