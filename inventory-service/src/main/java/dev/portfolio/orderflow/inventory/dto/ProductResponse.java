package dev.portfolio.orderflow.inventory.dto;

import dev.portfolio.orderflow.inventory.domain.Product;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        int availableQuantity,
        int reservedQuantity
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getAvailableQuantity(),
                product.getReservedQuantity()
        );
    }
}
