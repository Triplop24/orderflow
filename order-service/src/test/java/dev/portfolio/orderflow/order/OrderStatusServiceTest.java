package dev.portfolio.orderflow.order;

import dev.portfolio.orderflow.order.domain.Order;
import dev.portfolio.orderflow.order.domain.OrderStatus;
import dev.portfolio.orderflow.order.messaging.InventoryResultEvent;
import dev.portfolio.orderflow.order.repository.OrderRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderStatusServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderStatusService orderStatusService;

    @Test
    void shouldConfirmOrderWhenInventoryReserved() {

        Order order = createOrder(10L);

        when(orderRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(order));

        orderStatusService.applyInventoryResult(
                new InventoryResultEvent(
                        10L,
                        "RESERVED",
                        null
                )
        );

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);

        verify(orderRepository).findByIdForUpdate(10L);
    }

    @Test
    void shouldRejectOrderWhenInventoryRejected() {

        Order order = createOrder(10L);

        when(orderRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(order));

        orderStatusService.applyInventoryResult(
                new InventoryResultEvent(
                        10L,
                        "REJECTED",
                        "Insufficient stock"
                )
        );

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void shouldIgnoreDuplicateResult() {

        Order order = createOrder(10L);
        order.confirm();

        when(orderRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(order));

        orderStatusService.applyInventoryResult(
                new InventoryResultEvent(
                        10L,
                        "RESERVED",
                        null
                )
        );

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void shouldIgnoreConflictingResult() {

        Order order = createOrder(10L);
        order.confirm();

        when(orderRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(order));

        orderStatusService.applyInventoryResult(
                new InventoryResultEvent(
                        10L,
                        "REJECTED",
                        "Insufficient stock"
                )
        );

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void shouldFailWhenOrderNotFound() {

        when(orderRepository.findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> orderStatusService.applyInventoryResult(
                        new InventoryResultEvent(
                                999L,
                                "RESERVED",
                                null
                        )
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void shouldRejectUnknownStatus() {

        assertThatThrownBy(
                () -> orderStatusService.applyInventoryResult(
                        new InventoryResultEvent(
                                10L,
                                "UNKNOWN",
                                null
                        )
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown inventory result status");

        verifyNoInteractions(orderRepository);
    }

    private Order createOrder(Long id) {

        Order order = new Order("customer@example.com");

        ReflectionTestUtils.setField(
                order,
                "id",
                id
        );

        return order;
    }
}
