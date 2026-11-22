package dev.portfolio.orderflow.inventory;

import dev.portfolio.orderflow.inventory.domain.Product;
import dev.portfolio.orderflow.inventory.domain.ReservationStatus;
import dev.portfolio.orderflow.inventory.messaging.InventoryResultEvent;
import dev.portfolio.orderflow.inventory.messaging.OrderCreatedEvent;
import dev.portfolio.orderflow.inventory.repository.InventoryReservationRepository;
import dev.portfolio.orderflow.inventory.repository.ProductRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Testcontainers
class OrderReservationServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("inventory_test")
                    .withUsername("orderflow")
                    .withPassword("orderflow");

    @DynamicPropertySource
    static void configureDatabase(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    private OrderReservationService reservationService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Test
    void shouldNotReserveStockTwiceForSameOrder() {

        Product product = createProduct(10);
        Long productId = product.getId();
        Long orderId = newOrderId();

        OrderCreatedEvent event = createEvent(
                orderId,
                productId,
                3
        );

        InventoryResultEvent first =
                reservationService.process(event);

        InventoryResultEvent second =
                reservationService.process(event);

        assertThat(first.status()).isEqualTo("RESERVED");
        assertThat(second.status()).isEqualTo("RESERVED");

        assertThat(first).isEqualTo(second);

        Product updated = productRepository.findById(productId)
                .orElseThrow();

        assertThat(updated.getAvailableQuantity())
                .isEqualTo(7);

        assertThat(updated.getReservedQuantity())
                .isEqualTo(3);

        assertThat(reservationRepository.findById(orderId))
                .get()
                .extracting("status")
                .isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void shouldRememberRejectedResult() {

        Product product = createProduct(2);
        Long productId = product.getId();
        Long orderId = newOrderId();

        OrderCreatedEvent event = createEvent(
                orderId,
                productId,
                5
        );

        InventoryResultEvent first =
                reservationService.process(event);

        InventoryResultEvent second =
                reservationService.process(event);

        assertThat(first.status()).isEqualTo("REJECTED");
        assertThat(second).isEqualTo(first);

        assertThat(first.reason())
                .contains("Insufficient stock");

        Product updated = productRepository.findById(productId)
                .orElseThrow();

        assertThat(updated.getAvailableQuantity())
                .isEqualTo(2);

        assertThat(updated.getReservedQuantity())
                .isZero();

        assertThat(reservationRepository.findById(orderId))
                .get()
                .extracting("status")
                .isEqualTo(ReservationStatus.REJECTED);
    }

    @Test
    void shouldProcessConcurrentDuplicatesOnlyOnce()
            throws Exception {

        Product product = createProduct(10);
        Long productId = product.getId();
        Long orderId = newOrderId();

        OrderCreatedEvent event = createEvent(
                orderId,
                productId,
                4
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {

            Future<InventoryResultEvent> first =
                    executor.submit(
                            () -> processConcurrently(
                                    event,
                                    ready,
                                    start
                            )
                    );

            Future<InventoryResultEvent> second =
                    executor.submit(
                            () -> processConcurrently(
                                    event,
                                    ready,
                                    start
                            )
                    );

            assertThat(
                    ready.await(10, TimeUnit.SECONDS)
            ).isTrue();

            start.countDown();

            InventoryResultEvent firstResult =
                    first.get(20, TimeUnit.SECONDS);

            InventoryResultEvent secondResult =
                    second.get(20, TimeUnit.SECONDS);

            assertThat(firstResult.status())
                    .isEqualTo("RESERVED");

            assertThat(secondResult.status())
                    .isEqualTo("RESERVED");

            Product updated = productRepository.findById(productId)
                    .orElseThrow();

            // Both events succeeded logically, but stock
            // was reserved only once.

            assertThat(updated.getAvailableQuantity())
                    .isEqualTo(6);

            assertThat(updated.getReservedQuantity())
                    .isEqualTo(4);

            assertThat(reservationRepository.findById(orderId))
                    .get()
                    .extracting("status")
                    .isEqualTo(ReservationStatus.RESERVED);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private InventoryResultEvent processConcurrently(
            OrderCreatedEvent event,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {

        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "Timed out waiting for concurrent start"
            );
        }

        return reservationService.process(event);
    }

    private Product createProduct(int quantity) {

        Product product = new Product(
                "TEST-" + UUID.randomUUID(),
                "Idempotency Test Product",
                quantity
        );

        return productRepository.saveAndFlush(product);
    }

    private Long newOrderId() {
        return ThreadLocalRandom.current()
                .nextLong(1_000_000L, Long.MAX_VALUE);
    }

    private OrderCreatedEvent createEvent(
            Long orderId,
            Long productId,
            int quantity
    ) {

        return new OrderCreatedEvent(
                orderId,
                List.of(
                        new OrderCreatedEvent.Item(
                                productId,
                                quantity
                        )
                )
        );
    }
}
