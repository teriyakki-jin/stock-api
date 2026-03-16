package com.nh.stockapi.domain.account.repository;

import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByMember(Member member);

    Optional<Account> findByAccountNumber(String accountNumber);

    // 잔액 변경 시 비관적 락 (동시 주문 처리 안전)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") Long id);

    boolean existsByMember(Member member);
}
