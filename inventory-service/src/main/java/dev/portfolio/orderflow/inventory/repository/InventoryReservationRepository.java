package dev.portfolio.orderflow.inventory.repository;

import dev.portfolio.orderflow.inventory.domain.InventoryReservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryReservationRepository
        extends JpaRepository<InventoryReservation, Long> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO inventory_reservations (
                        order_id,
                        status
                    )
                    VALUES (
                        :orderId,
                        'PROCESSING'
                    )
                    ON CONFLICT (order_id) DO NOTHING
                    """,
            nativeQuery = true
    )
    int beginIfAbsent(@Param("orderId") Long orderId);
}
