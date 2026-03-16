package com.nh.stockapi.domain.order.repository;

import com.nh.stockapi.domain.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o JOIN FETCH o.stock WHERE o.account.id = :accountId")
    Page<Order> findByAccountId(@Param("accountId") Long accountId, Pageable pageable);
}
