package dev.portfolio.orderflow.order;

import dev.portfolio.orderflow.order.domain.Order;
import dev.portfolio.orderflow.order.domain.OrderItem;
import dev.portfolio.orderflow.order.domain.OrderStatus;
import dev.portfolio.orderflow.order.dto.CreateOrderRequest;
import dev.portfolio.orderflow.order.dto.OrderItemRequest;
import dev.portfolio.orderflow.order.dto.OrderResponse;
import dev.portfolio.orderflow.order.messaging.OrderCreatedEvent;
import dev.portfolio.orderflow.order.repository.OrderRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldCreateOrderAndPublishEvent() {

        CreateOrderRequest request = new CreateOrderRequest(
                "  CUSTOMER@EXAMPLE.COM  ",
                List.of(
                        new OrderItemRequest(101L, 2),
                        new OrderItemRequest(205L, 1)
                )
        );

        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenAnswer(invocation -> {

                    Order order = invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            order,
                            "id",
                            10L
                    );

                    long itemId = 1L;

                    for (OrderItem item : order.getItems()) {
                        ReflectionTestUtils.setField(
                                item,
                                "id",
                                itemId++
                        );
                    }

                    return order;
                });

        OrderResponse response = orderService.create(request);

        assertThat(response.id()).isEqualTo(10L);

        assertThat(response.customerEmail())
                .isEqualTo("customer@example.com");

        assertThat(response.status())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(response.items()).hasSize(2);

        assertThat(response.items().get(0).productId())
                .isEqualTo(101L);

        assertThat(response.items().get(0).quantity())
                .isEqualTo(2);

        assertThat(response.items().get(1).productId())
                .isEqualTo(205L);

        assertThat(response.items().get(1).quantity())
                .isEqualTo(1);

        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();

        ArgumentCaptor<Order> orderCaptor =
                ArgumentCaptor.forClass(Order.class);

        verify(orderRepository)
                .saveAndFlush(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getItems()).hasSize(2);

        ArgumentCaptor<OrderCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderCreatedEvent.class);

        verify(eventPublisher).publishEvent(
                eventCaptor.capture()
        );

        OrderCreatedEvent event = eventCaptor.getValue();

        assertThat(event.orderId()).isEqualTo(10L);

        assertThat(event.items()).containsExactly(
                new OrderCreatedEvent.Item(101L, 2),
                new OrderCreatedEvent.Item(205L, 1)
        );
    }

    @Test
    void shouldRejectDuplicateProducts() {

        CreateOrderRequest request = new CreateOrderRequest(
                "customer@example.com",
                List.of(
                        new OrderItemRequest(101L, 2),
                        new OrderItemRequest(101L, 3)
                )
        );

        ResponseStatusException exception =
                catchThrowableOfType(
                        () -> orderService.create(request),
                        ResponseStatusException.class
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(exception.getReason())
                .contains("Duplicate product");

        verify(orderRepository, never())
                .saveAndFlush(any(Order.class));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldGetOrderById() {

        Order order = createSavedOrder(10L);

        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(order));

        OrderResponse response = orderService.getById(10L);

        assertThat(response.id()).isEqualTo(10L);

        assertThat(response.customerEmail())
                .isEqualTo("customer@example.com");

        assertThat(response.status())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(response.items()).hasSize(1);
    }

    @Test
    void shouldReturn404WhenOrderNotFound() {

        when(orderRepository.findById(999L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                catchThrowableOfType(
                        () -> orderService.getById(999L),
                        ResponseStatusException.class
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(exception.getReason())
                .contains("Order not found");
    }

    @Test
    void shouldReturnAllOrders() {

        Order first = createSavedOrder(2L);
        Order second = createSavedOrder(1L);

        when(orderRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(first, second));

        List<OrderResponse> result = orderService.getAll();

        assertThat(result).hasSize(2);

        assertThat(result)
                .extracting(OrderResponse::id)
                .containsExactly(2L, 1L);
    }

    private Order createSavedOrder(Long id) {

        Order order = new Order("customer@example.com");

        order.addItem(101L, 2);

        ReflectionTestUtils.setField(order, "id", id);

        ReflectionTestUtils.setField(
                order.getItems().getFirst(),
                "id",
                id * 10
        );

        return order;
    }
}
