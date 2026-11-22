package dev.portfolio.orderflow.inventory.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private Instant createdAt;

    protected InventoryReservation() {
    }

    public void markReserved() {
        ensureProcessing();

        this.status = ReservationStatus.RESERVED;
        this.reason = null;
    }

    public void markRejected(String reason) {
        ensureProcessing();

        this.status = ReservationStatus.REJECTED;
        this.reason = reason;
    }

    private void ensureProcessing() {
        if (status != ReservationStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Reservation has already been processed"
            );
        }
    }

    public Long getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
