package dev.portfolio.orderflow.order.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Entity(name = "CustomerOrder")
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_email", nullable = false, length = 255)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
    }

    public Order(String customerEmail) {
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "Customer email must not be blank"
            );
        }

        this.customerEmail = customerEmail
                .trim()
                .toLowerCase(Locale.ROOT);

        this.status = OrderStatus.PENDING;

        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addItem(Long productId, int quantity) {
        boolean alreadyExists = items.stream()
                .anyMatch(item -> item.getProductId().equals(productId));

        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "Duplicate product in order: " + productId
            );
        }

        OrderItem item = new OrderItem(
                this,
                productId,
                quantity
        );

        items.add(item);
    }

    public void confirm() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending orders can be confirmed"
            );
        }

        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void reject() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending orders can be rejected"
            );
        }

        this.status = OrderStatus.REJECTED;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
