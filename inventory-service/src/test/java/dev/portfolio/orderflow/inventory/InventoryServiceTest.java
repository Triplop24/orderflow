package dev.portfolio.orderflow.inventory;

import dev.portfolio.orderflow.inventory.domain.Product;
import dev.portfolio.orderflow.inventory.dto.InventoryReservationResponse;
import dev.portfolio.orderflow.inventory.dto.ProductResponse;
import dev.portfolio.orderflow.inventory.dto.ReserveInventoryRequest;
import dev.portfolio.orderflow.inventory.repository.ProductRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void shouldReturnAllProducts() {
        Product laptop = createProduct(101L, 10);
        Product mouse = createProduct(205L, 50);

        when(productRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(laptop, mouse));

        List<ProductResponse> result =
                inventoryService.getAllProducts();

        assertThat(result).hasSize(2);

        assertThat(result)
                .extracting(ProductResponse::id)
                .containsExactly(101L, 205L);
    }

    @Test
    void shouldReturnProductById() {
        Product product = createProduct(101L, 10);

        when(productRepository.findById(101L))
                .thenReturn(Optional.of(product));

        ProductResponse response =
                inventoryService.getProductById(101L);

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.availableQuantity()).isEqualTo(10);
        assertThat(response.reservedQuantity()).isZero();
    }

    @Test
    void shouldReturn404WhenProductNotFound() {
        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                catchThrowableOfType(
                        () -> inventoryService.getProductById(999L),
                        ResponseStatusException.class
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReserveMultipleProductsInIdOrder() {
        Product laptop = createProduct(101L, 10);
        Product mouse = createProduct(205L, 50);

        when(productRepository.findByIdForUpdate(101L))
                .thenReturn(Optional.of(laptop));

        when(productRepository.findByIdForUpdate(205L))
                .thenReturn(Optional.of(mouse));

        // Items deliberately arrive in reverse ID order.
        ReserveInventoryRequest request =
                new ReserveInventoryRequest(
                        List.of(
                                new ReserveInventoryRequest.Item(205L, 1),
                                new ReserveInventoryRequest.Item(101L, 2)
                        )
                );

        InventoryReservationResponse response =
                inventoryService.reserve(request);

        assertThat(response.status()).isEqualTo("RESERVED");
        assertThat(response.items()).hasSize(2);

        assertThat(laptop.getAvailableQuantity()).isEqualTo(8);
        assertThat(laptop.getReservedQuantity()).isEqualTo(2);

        assertThat(mouse.getAvailableQuantity()).isEqualTo(49);
        assertThat(mouse.getReservedQuantity()).isEqualTo(1);

        InOrder inOrder = inOrder(productRepository);

        inOrder.verify(productRepository)
                .findByIdForUpdate(101L);

        inOrder.verify(productRepository)
                .findByIdForUpdate(205L);

        assertThat(response.items().get(0).productId())
                .isEqualTo(101L);

        assertThat(response.items().get(0).remainingAvailable())
                .isEqualTo(8);
    }

    @Test
    void shouldRejectReservationWhenStockIsInsufficient() {
        Product product = createProduct(101L, 3);

        when(productRepository.findByIdForUpdate(101L))
                .thenReturn(Optional.of(product));

        ReserveInventoryRequest request =
                new ReserveInventoryRequest(
                        List.of(
                                new ReserveInventoryRequest.Item(101L, 5)
                        )
                );

        ResponseStatusException exception =
                catchThrowableOfType(
                        () -> inventoryService.reserve(request),
                        ResponseStatusException.class
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(product.getAvailableQuantity()).isEqualTo(3);
        assertThat(product.getReservedQuantity()).isZero();
    }

    @Test
    void shouldRejectReservationForMissingProduct() {
        when(productRepository.findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        ReserveInventoryRequest request =
                new ReserveInventoryRequest(
                        List.of(
                                new ReserveInventoryRequest.Item(999L, 2)
                        )
                );

        ResponseStatusException exception =
                catchThrowableOfType(
                        () -> inventoryService.reserve(request),
                        ResponseStatusException.class
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldRejectDuplicateProducts() {
        ReserveInventoryRequest request =
                new ReserveInventoryRequest(
                        List.of(
                                new ReserveInventoryRequest.Item(101L, 2),
                                new ReserveInventoryRequest.Item(101L, 3)
                        )
                );

        ResponseStatusException exception =
                catchThrowableOfType(
                        () -> inventoryService.reserve(request),
                        ResponseStatusException.class
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldRejectInvalidQuantity() {
        ReserveInventoryRequest request =
                new ReserveInventoryRequest(
                        List.of(
                                new ReserveInventoryRequest.Item(101L, 0)
                        )
                );

        ResponseStatusException exception =
                catchThrowableOfType(
                        () -> inventoryService.reserve(request),
                        ResponseStatusException.class
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldRejectEmptyReservation() {
        ReserveInventoryRequest request =
                new ReserveInventoryRequest(List.of());

        ResponseStatusException exception =
                catchThrowableOfType(
                        () -> inventoryService.reserve(request),
                        ResponseStatusException.class
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(productRepository);
    }

    private Product createProduct(Long id, int availableQuantity) {
        Product product = new Product(
                "SKU-" + id,
                "Test Product",
                availableQuantity
        );

        ReflectionTestUtils.setField(product, "id", id);

        return product;
    }
}
