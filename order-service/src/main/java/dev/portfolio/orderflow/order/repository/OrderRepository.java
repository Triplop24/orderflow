package dev.portfolio.orderflow.order.repository;

import dev.portfolio.orderflow.order.domain.Order;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository
        extends JpaRepository<Order, Long> {

    List<Order> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o
            FROM CustomerOrder o
            WHERE o.id = :id
            """)
    Optional<Order> findByIdForUpdate(
            @Param("id") Long id
    );
}
