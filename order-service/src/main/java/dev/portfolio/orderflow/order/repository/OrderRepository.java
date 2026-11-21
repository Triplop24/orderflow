package dev.portfolio.orderflow.order.repository;

import dev.portfolio.orderflow.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository
        extends JpaRepository<Order, Long> {

    List<Order> findAllByOrderByCreatedAtDesc();
}
