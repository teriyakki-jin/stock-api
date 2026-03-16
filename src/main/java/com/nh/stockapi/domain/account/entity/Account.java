package com.nh.stockapi.domain.account.entity;

import com.nh.stockapi.common.entity.BaseEntity;
import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Builder
    private Account(Member member) {
        this.accountNumber = generateAccountNumber();
        this.member = member;
        this.balance = BigDecimal.ZERO;
        this.status = AccountStatus.ACTIVE;
    }

    public static Account open(Member member) {
        return Account.builder().member(member).build();
    }

    public void deposit(BigDecimal amount) {
        validateActive();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        this.balance = this.balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        validateActive();
        if (this.balance.compareTo(amount) < 0) {
            throw new CustomException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        this.balance = this.balance.subtract(amount);
    }

    public void close() {
        validateActive();
        this.status = AccountStatus.CLOSED;
    }

    private void validateActive() {
        if (this.status == AccountStatus.CLOSED) {
            throw new CustomException(ErrorCode.ACCOUNT_CLOSED);
        }
    }

    private static String generateAccountNumber() {
        // 형식: NH-XXXXXXXXXXXXXXXX (NH + 16자리 UUID 기반)
        return "NH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
