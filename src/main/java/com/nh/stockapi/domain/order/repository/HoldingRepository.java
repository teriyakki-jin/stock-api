package com.nh.stockapi.domain.order.repository;

import com.nh.stockapi.domain.order.entity.Holding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Holding h WHERE h.account.id = :accountId AND h.stock.id = :stockId")
    Optional<Holding> findByAccountIdAndStockIdWithLock(
            @Param("accountId") Long accountId,
            @Param("stockId") Long stockId);

    @Query("SELECT h FROM Holding h WHERE h.account.id = :accountId AND h.stock.id = :stockId")
    Optional<Holding> findByAccountIdAndStockId(
            @Param("accountId") Long accountId,
            @Param("stockId") Long stockId);

    @Query("SELECT h FROM Holding h JOIN FETCH h.stock WHERE h.account.id = :accountId AND h.quantity > 0")
    List<Holding> findByAccountIdWithStock(@Param("accountId") Long accountId);
}
