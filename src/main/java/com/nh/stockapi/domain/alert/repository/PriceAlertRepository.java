package com.nh.stockapi.domain.alert.repository;

import com.nh.stockapi.domain.alert.entity.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    List<PriceAlert> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    @Query("SELECT a FROM PriceAlert a WHERE a.ticker = :ticker AND a.active = true")
    List<PriceAlert> findActiveByTicker(@Param("ticker") String ticker);

    @Query("SELECT a FROM PriceAlert a WHERE a.account.id = :accountId AND a.triggered = true AND a.acknowledged = false")
    List<PriceAlert> findUnacknowledgedByAccountId(@Param("accountId") Long accountId);
}
