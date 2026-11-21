package dev.portfolio.orderflow.inventory;

import dev.portfolio.orderflow.inventory.domain.Product;
import dev.portfolio.orderflow.inventory.dto.InventoryReservationResponse;
import dev.portfolio.orderflow.inventory.dto.ProductResponse;
import dev.portfolio.orderflow.inventory.dto.ReserveInventoryRequest;
import dev.portfolio.orderflow.inventory.repository.ProductRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final ProductRepository productRepository;

    public InventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductResponse> getAllProducts() {

        return productRepository
                .findAllByOrderByIdAsc()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse getProductById(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(
                        () -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Product not found: " + id
                        )
                );

        return ProductResponse.from(product);
    }

    @Transactional
    public InventoryReservationResponse reserve(
            ReserveInventoryRequest request
    ) {

        if (request == null
                || request.items() == null
                || request.items().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reservation must contain at least one item"
            );
        }

        Set<Long> productIds = new HashSet<>();

        for (ReserveInventoryRequest.Item item : request.items()) {

            if (item == null
                    || item.productId() == null
                    || item.productId() <= 0
                    || item.quantity() == null
                    || item.quantity() <= 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid reservation item"
                );
            }

            if (!productIds.add(item.productId())) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate product: " + item.productId()
                );
            }
        }

        // Lock products in a consistent order to reduce
        // the risk of deadlocks between concurrent requests.

        List<ReserveInventoryRequest.Item> sortedItems =
                request.items()
                        .stream()
                        .sorted(
                                Comparator.comparing(
                                        ReserveInventoryRequest.Item::productId
                                )
                        )
                        .toList();

        List<InventoryReservationResponse.Item> reservedItems =
                new ArrayList<>();

        for (ReserveInventoryRequest.Item item : sortedItems) {

            Product product = productRepository
                    .findByIdForUpdate(item.productId())
                    .orElseThrow(
                            () -> new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Product not found: "
                                            + item.productId()
                            )
                    );

            if (product.getAvailableQuantity() < item.quantity()) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Insufficient stock for product: "
                                + product.getId()
                );
            }

            product.reserve(item.quantity());

            reservedItems.add(
                    new InventoryReservationResponse.Item(
                            product.getId(),
                            item.quantity(),
                            product.getAvailableQuantity(),
                            product.getReservedQuantity()
                    )
            );
        }

        return new InventoryReservationResponse(
                "RESERVED",
                reservedItems
        );
    }
}
