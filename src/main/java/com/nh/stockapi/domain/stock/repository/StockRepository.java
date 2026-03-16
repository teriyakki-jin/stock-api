package com.nh.stockapi.domain.stock.repository;

import com.nh.stockapi.domain.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByTicker(String ticker);

    List<Stock> findByNameContainingIgnoreCaseOrTickerContaining(String name, String ticker);

    boolean existsByTicker(String ticker);
}
