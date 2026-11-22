package dev.portfolio.orderflow.order;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.portfolio.orderflow.order.domain.Order;
import dev.portfolio.orderflow.order.domain.OrderItem;
import dev.portfolio.orderflow.order.domain.OrderStatus;

import dev.portfolio.orderflow.order.dto.CreateOrderRequest;
import dev.portfolio.orderflow.order.dto.OrderItemRequest;
import dev.portfolio.orderflow.order.dto.OrderResponse;

import dev.portfolio.orderflow.order.outbox.OutboxEvent;
import dev.portfolio.orderflow.order.outbox.OutboxEventRepository;
import dev.portfolio.orderflow.order.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private OutboxEventRepository outboxEventRepository;

    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                outboxEventRepository,
                objectMapper
        );
    }

    @Test
    void shouldCreateOrderAndSaveOutboxEvent() throws Exception {

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

        ArgumentCaptor<OutboxEvent> captor =
                ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent outboxEvent = captor.getValue();

        assertThat(outboxEvent.getAggregateId())
                .isEqualTo(10L);

        assertThat(outboxEvent.getEventType())
                .isEqualTo("OrderCreated");

        assertThat(outboxEvent.getPublishedAt()).isNull();

        var json = objectMapper.readTree(
                outboxEvent.getPayload()
        );

        assertThat(json.get("orderId").asLong())
                .isEqualTo(10L);

        assertThat(json.get("items").size())
                .isEqualTo(2);

        assertThat(
                json.get("items").get(0).get("productId").asLong()
        ).isEqualTo(101L);

        assertThat(
                json.get("items").get(0).get("quantity").asInt()
        ).isEqualTo(2);

        assertThat(
                json.get("items").get(1).get("productId").asLong()
        ).isEqualTo(205L);
    }

    @Test
    void shouldRejectDuplicateProductsWithoutSaving() {

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

        verify(orderRepository, never())
                .saveAndFlush(any(Order.class));

        verifyNoInteractions(outboxEventRepository);
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
