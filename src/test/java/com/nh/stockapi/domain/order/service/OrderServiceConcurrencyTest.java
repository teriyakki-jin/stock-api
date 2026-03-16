package com.nh.stockapi.domain.order.service;

import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.account.repository.AccountRepository;
import com.nh.stockapi.domain.member.entity.Member;
import com.nh.stockapi.domain.member.repository.MemberRepository;
import com.nh.stockapi.domain.order.dto.OrderRequest;
import com.nh.stockapi.domain.order.entity.OrderType;
import com.nh.stockapi.domain.stock.entity.Stock;
import com.nh.stockapi.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비관적 락 동시성 테스트
 * - 10개 스레드가 동시에 매수 주문 시 잔액이 정확히 차감되는지 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("OrderService 동시성 테스트")
class OrderServiceConcurrencyTest {

    @Autowired OrderService orderService;
    @Autowired MemberRepository memberRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired StockRepository stockRepository;

    Member member;
    Account account;
    Stock stock;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(
                Member.create("concurrent@test.com", "encoded", "동시성테스터", "010-9999-0000"));

        account = accountRepository.save(Account.open(member));
        account.deposit(new BigDecimal("10000000")); // 1000만원
        accountRepository.save(account);

        stock = stockRepository.save(Stock.builder()
                .ticker("CONC001")
                .name("동시성테스트종목")
                .market("TEST")
                .basePrice(new BigDecimal("10000"))
                .totalShares(1_000_000L)
                .build());
    }

    @Test
    @DisplayName("10개 스레드 동시 매수 — 비관적 락으로 잔액 정합성 보장")
    void concurrentBuy_shouldMaintainBalanceConsistency() throws InterruptedException {
        int threadCount = 10;
        long quantityPerThread = 1L;
        BigDecimal pricePerUnit = new BigDecimal("10000");
        BigDecimal expectedDeduction = pricePerUnit.multiply(new BigDecimal(threadCount));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        BigDecimal balanceBefore = accountRepository.findById(account.getId())
                .orElseThrow().getBalance();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    OrderRequest req = new OrderRequest("CONC001", OrderType.BUY, quantityPerThread);
                    orderService.buy(account.getId(), member, req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        BigDecimal balanceAfter = accountRepository.findById(account.getId())
                .orElseThrow().getBalance();

        BigDecimal actualDeduction = balanceBefore.subtract(balanceAfter);

        // 성공한 주문 수 * 단가 = 실제 차감액
        assertThat(actualDeduction)
                .isEqualByComparingTo(pricePerUnit.multiply(BigDecimal.valueOf(successCount.get())));

        System.out.printf("성공: %d건, 실패: %d건, 차감액: %s원%n",
                successCount.get(), failCount.get(), actualDeduction.toPlainString());
    }
}
