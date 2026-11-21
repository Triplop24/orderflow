package dev.portfolio.orderflow.inventory;

import dev.portfolio.orderflow.inventory.domain.Product;
import dev.portfolio.orderflow.inventory.dto.ReserveInventoryRequest;
import dev.portfolio.orderflow.inventory.repository.ProductRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

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
class InventoryConcurrencyIntegrationTest {

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
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldPreventOversellingUnderConcurrentRequests()
            throws Exception {

        Product product = createProduct(5);
        Long productId = product.getId();

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<String> first = executor.submit(
                    () -> attemptReservation(
                            productId, ready, start
                    )
            );

            Future<String> second = executor.submit(
                    () -> attemptReservation(
                            productId, ready, start
                    )
            );

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .isTrue();

            start.countDown();

            String firstResult =
                    first.get(20, TimeUnit.SECONDS);

            String secondResult =
                    second.get(20, TimeUnit.SECONDS);

            assertThat(List.of(firstResult, secondResult))
                    .containsExactlyInAnyOrder(
                            "RESERVED",
                            "CONFLICT"
                    );

            Product updated = productRepository.findById(productId)
                    .orElseThrow();

            assertThat(updated.getAvailableQuantity())
                    .isEqualTo(1);

            assertThat(updated.getReservedQuantity())
                    .isEqualTo(4);

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRollbackAllItemsIfOneProductHasInsufficientStock() {

        Product firstProduct = createProduct(10);
        Product secondProduct = createProduct(1);

        Long firstId = firstProduct.getId();
        Long secondId = secondProduct.getId();

        ReserveInventoryRequest request =
                new ReserveInventoryRequest(
                        List.of(
                                new ReserveInventoryRequest.Item(
                                        firstId, 3
                                ),
                                new ReserveInventoryRequest.Item(
                                        secondId, 2
                                )
                        )
                );

        ResponseStatusException exception =
                catchThrowableOfType(
                        () -> inventoryService.reserve(request),
                        ResponseStatusException.class
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Product firstAfter = productRepository.findById(firstId)
                .orElseThrow();

        Product secondAfter = productRepository.findById(secondId)
                .orElseThrow();

        // The first successful reservation must be rolled back.
        assertThat(firstAfter.getAvailableQuantity())
                .isEqualTo(10);

        assertThat(firstAfter.getReservedQuantity())
                .isZero();

        assertThat(secondAfter.getAvailableQuantity())
                .isEqualTo(1);

        assertThat(secondAfter.getReservedQuantity())
                .isZero();
    }

    private String attemptReservation(
            Long productId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {

        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "Timeout waiting for concurrent start"
            );
        }

        ReserveInventoryRequest request =
                new ReserveInventoryRequest(
                        List.of(
                                new ReserveInventoryRequest.Item(
                                        productId, 4
                                )
                        )
                );

        try {
            inventoryService.reserve(request);
            return "RESERVED";

        } catch (ResponseStatusException exception) {

            if (exception.getStatusCode()
                    .equals(HttpStatus.CONFLICT)) {
                return "CONFLICT";
            }

            throw exception;
        }
    }

    private Product createProduct(int availableQuantity) {
        Product product = new Product(
                "TEST-" + UUID.randomUUID(),
                "Concurrency Test Product",
                availableQuantity
        );

        return productRepository.saveAndFlush(product);
    }
}
