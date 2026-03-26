package com.nh.stockapi.domain.portfolio.service;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.account.service.AccountService;
import com.nh.stockapi.domain.member.entity.Member;
import com.nh.stockapi.domain.order.entity.Holding;
import com.nh.stockapi.domain.order.entity.Order;
import com.nh.stockapi.domain.order.entity.OrderType;
import com.nh.stockapi.domain.order.repository.HoldingRepository;
import com.nh.stockapi.domain.order.repository.OrderRepository;
import com.nh.stockapi.domain.portfolio.dto.PortfolioAnalysisResponse;
import com.nh.stockapi.domain.portfolio.dto.PortfolioSummaryResponse;
import com.nh.stockapi.domain.stock.entity.Stock;
import com.nh.stockapi.domain.stock.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PortfolioService 단위 테스트")
class PortfolioServiceTest {

    @InjectMocks PortfolioService portfolioService;
    @Mock AccountService accountService;
    @Mock HoldingRepository holdingRepository;
    @Mock OrderRepository orderRepository;
    @Mock StockService stockService;

    Member member;
    Member otherMember;
    Account account;
    Stock stock;

    @BeforeEach
    void setUp() {
        member = Member.create("test@test.com", "encoded-pw", "테스터", "010-0000-0000");
        ReflectionTestUtils.setField(member, "id", 1L);

        otherMember = Member.create("other@test.com", "encoded-pw", "타인", "010-1111-2222");
        ReflectionTestUtils.setField(otherMember, "id", 99L);

        account = Account.open(member);
        ReflectionTestUtils.setField(account, "id", 100L);
        account.deposit(new BigDecimal("10000000"));

        stock = Stock.builder()
                .ticker("005930")
                .name("삼성전자")
                .market("KOSPI")
                .basePrice(new BigDecimal("75000"))
                .totalShares(5_969_782_550L)
                .build();
        ReflectionTestUtils.setField(stock, "id", 10L);
    }

    // ── 포트폴리오 요약 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("포트폴리오 요약 — 보유 종목 없음")
    void getSummary_empty() {
        // given
        given(accountService.findById(100L)).willReturn(account);
        given(holdingRepository.findByAccountIdWithStock(100L)).willReturn(List.of());

        // when
        PortfolioSummaryResponse response = portfolioService.getSummary(100L, member);

        // then
        assertThat(response.totalInvested()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.currentValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalPnl()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.pnlRate()).isEqualTo(0.0);
        assertThat(response.holdings()).isEmpty();
    }

    @Test
    @DisplayName("포트폴리오 요약 — 수익 종목 포함")
    void getSummary_withHolding_profit() {
        // given: 삼성전자 10주 평단가 70,000원, 현재가 80,000원
        Holding holding = Holding.of(account, stock, 10L, new BigDecimal("70000"));
        ReflectionTestUtils.setField(holding, "id", 1L);

        given(accountService.findById(100L)).willReturn(account);
        given(holdingRepository.findByAccountIdWithStock(100L)).willReturn(List.of(holding));
        given(stockService.getCurrentPrice(eq("005930"), any())).willReturn(new BigDecimal("80000"));

        // when
        PortfolioSummaryResponse response = portfolioService.getSummary(100L, member);

        // then
        BigDecimal expectedInvested = new BigDecimal("700000");  // 70,000 × 10
        BigDecimal expectedValue    = new BigDecimal("800000");  // 80,000 × 10
        BigDecimal expectedPnl      = new BigDecimal("100000");  // 100,000 수익

        assertThat(response.totalInvested()).isEqualByComparingTo(expectedInvested);
        assertThat(response.currentValue()).isEqualByComparingTo(expectedValue);
        assertThat(response.totalPnl()).isEqualByComparingTo(expectedPnl);
        assertThat(response.pnlRate()).isGreaterThan(0); // 양수 수익률
        assertThat(response.holdings()).hasSize(1);
        assertThat(response.holdings().get(0).ticker()).isEqualTo("005930");
    }

    @Test
    @DisplayName("포트폴리오 요약 — 손실 종목")
    void getSummary_withHolding_loss() {
        // given: 평단가 80,000 → 현재가 70,000 (손실)
        Holding holding = Holding.of(account, stock, 5L, new BigDecimal("80000"));
        ReflectionTestUtils.setField(holding, "id", 1L);

        given(accountService.findById(100L)).willReturn(account);
        given(holdingRepository.findByAccountIdWithStock(100L)).willReturn(List.of(holding));
        given(stockService.getCurrentPrice(eq("005930"), any())).willReturn(new BigDecimal("70000"));

        // when
        PortfolioSummaryResponse response = portfolioService.getSummary(100L, member);

        // then
        assertThat(response.totalPnl()).isNegative();
        assertThat(response.pnlRate()).isLessThan(0);
    }

    @Test
    @DisplayName("포트폴리오 요약 실패 — 타인 계좌")
    void getSummary_fail_forbidden() {
        given(accountService.findById(100L)).willReturn(account);

        assertThatThrownBy(() -> portfolioService.getSummary(100L, otherMember))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ── 포트폴리오 심화 분석 ──────────────────────────────────────────────────

    @Test
    @DisplayName("심화 분석 — 체결 주문 없음")
    void getAnalysis_noOrders() {
        // given
        given(accountService.findById(100L)).willReturn(account);
        given(orderRepository.findFilledSince(eq(100L), any(LocalDateTime.class))).willReturn(List.of());
        given(holdingRepository.findByAccountIdWithStock(100L)).willReturn(List.of());

        // when
        PortfolioAnalysisResponse response = portfolioService.getAnalysis(100L, member, 30);

        // then
        assertThat(response.winRate()).isEqualTo(0.0);
        assertThat(response.mdd()).isEqualTo(0.0);
        assertThat(response.totalTrades()).isEqualTo(0);
        assertThat(response.pnlCurve()).isEmpty();
        assertThat(response.sectorWeights()).isEmpty();
    }

    @Test
    @DisplayName("심화 분석 — 매수/매도 주문 포함 승률 계산")
    void getAnalysis_withOrders_winRate() {
        // given: 매수 후 고가 매도(수익), 매수 후 저가 매도(손실)
        Order buy1  = createFilledOrder(OrderType.BUY,  new BigDecimal("70000"), 5L);
        Order sell1 = createFilledOrder(OrderType.SELL, new BigDecimal("80000"), 5L); // 수익
        Order buy2  = createFilledOrder(OrderType.BUY,  new BigDecimal("90000"), 3L);
        Order sell2 = createFilledOrder(OrderType.SELL, new BigDecimal("75000"), 3L); // 손실

        given(accountService.findById(100L)).willReturn(account);
        given(orderRepository.findFilledSince(eq(100L), any(LocalDateTime.class)))
                .willReturn(List.of(buy1, sell1, buy2, sell2));
        given(holdingRepository.findByAccountIdWithStock(100L)).willReturn(List.of());

        // when
        PortfolioAnalysisResponse response = portfolioService.getAnalysis(100L, member, 30);

        // then
        assertThat(response.totalTrades()).isEqualTo(4); // 전체 체결 주문
        assertThat(response.profitTrades()).isEqualTo(1);
        assertThat(response.lossTrades()).isEqualTo(1);
        assertThat(response.winRate()).isEqualTo(50.0); // 1승 1패
    }

    @Test
    @DisplayName("심화 분석 실패 — 타인 계좌")
    void getAnalysis_fail_forbidden() {
        given(accountService.findById(100L)).willReturn(account);

        assertThatThrownBy(() -> portfolioService.getAnalysis(100L, otherMember, 30))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private Order createFilledOrder(OrderType orderType, BigDecimal unitPrice, Long qty) {
        Order order = Order.execute(account, stock, orderType, qty, unitPrice);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now());
        return order;
    }
}
