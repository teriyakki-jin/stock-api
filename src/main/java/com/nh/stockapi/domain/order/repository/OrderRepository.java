package com.nh.stockapi.domain.order.repository;

import com.nh.stockapi.domain.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o JOIN FETCH o.stock WHERE o.account.id = :accountId ORDER BY o.createdAt DESC")
    Page<Order> findByAccountId(@Param("accountId") Long accountId, Pageable pageable);

    @Query("SELECT o FROM Order o JOIN FETCH o.stock JOIN FETCH o.account a JOIN FETCH a.member " +
           "WHERE o.stock.ticker = :ticker AND o.status = 'PENDING' AND o.orderType = 'BUY_LIMIT' " +
           "AND o.limitPrice >= :currentPrice")
    List<Order> findPendingBuyLimitToFill(@Param("ticker") String ticker,
                                          @Param("currentPrice") BigDecimal currentPrice);

    @Query("SELECT o FROM Order o JOIN FETCH o.stock JOIN FETCH o.account a JOIN FETCH a.member " +
           "WHERE o.stock.ticker = :ticker AND o.status = 'PENDING' AND o.orderType = 'SELL_LIMIT' " +
           "AND o.limitPrice <= :currentPrice")
    List<Order> findPendingSellLimitToFill(@Param("ticker") String ticker,
                                           @Param("currentPrice") BigDecimal currentPrice);

    @Query("SELECT o FROM Order o JOIN FETCH o.stock WHERE o.account.id = :accountId AND o.status = 'PENDING' ORDER BY o.createdAt DESC")
    List<Order> findPendingByAccountId(@Param("accountId") Long accountId);
}
