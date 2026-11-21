package dev.portfolio.orderflow.inventory.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(
            String sku,
            String name,
            int availableQuantity
    ) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException(
                    "SKU must not be blank"
            );
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Product name must not be blank"
            );
        }

        if (availableQuantity < 0) {
            throw new IllegalArgumentException(
                    "Available quantity cannot be negative"
            );
        }

        this.sku = sku.trim();
        this.name = name.trim();
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = 0;

        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void reserve(int quantity) {

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Reservation quantity must be positive"
            );
        }

        if (availableQuantity < quantity) {
            throw new IllegalStateException(
                    "Insufficient stock for product: " + id
            );
        }

        this.reservedQuantity = Math.addExact(
                this.reservedQuantity,
                quantity
        );

        this.availableQuantity -= quantity;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
