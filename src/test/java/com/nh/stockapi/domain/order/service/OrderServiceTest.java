package com.nh.stockapi.domain.order.service;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.account.service.AccountService;
import com.nh.stockapi.domain.member.entity.Member;
import com.nh.stockapi.domain.order.dto.OrderRequest;
import com.nh.stockapi.domain.order.dto.OrderResponse;
import com.nh.stockapi.domain.order.entity.Holding;
import com.nh.stockapi.domain.order.entity.OrderType;
import com.nh.stockapi.domain.order.repository.HoldingRepository;
import com.nh.stockapi.domain.order.repository.OrderRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderService 단위 테스트")
class OrderServiceTest {

    @InjectMocks OrderService orderService;

    @Mock AccountService accountService;
    @Mock StockService stockService;
    @Mock HoldingRepository holdingRepository;
    @Mock OrderRepository orderRepository;

    Member member;
    Account account;
    Stock stock;

    @BeforeEach
    void setUp() {
        member  = Member.create("test@test.com", "encoded-pw", "테스터", "010-0000-0000");
        ReflectionTestUtils.setField(member, "id", 1L);  // validateOwner용 ID 주입
        account = Account.open(member);
        ReflectionTestUtils.setField(account, "id", 100L);
        account.deposit(new BigDecimal("1000000")); // 초기 잔액 100만원

        stock = Stock.builder()
                .ticker("005930")
                .name("삼성전자")
                .market("KOSPI")
                .basePrice(new BigDecimal("75000"))
                .totalShares(5_969_782_550L)
                .build();
        ReflectionTestUtils.setField(stock, "id", 10L);
    }

    // ─── 매수 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("매수 성공 — 잔액 차감 및 보유 수량 증가")
    void buy_success() {
        // given
        given(accountService.findWithLock(anyLong())).willReturn(account);
        given(stockService.findStockOrThrow("005930")).willReturn(stock);
        given(stockService.getCurrentPrice(anyString(), any())).willReturn(new BigDecimal("75000"));
        given(holdingRepository.findByAccountIdAndStockIdWithLock(anyLong(), anyLong()))
                .willReturn(Optional.empty());
        given(holdingRepository.save(any(Holding.class))).willAnswer(inv -> inv.getArgument(0));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        OrderRequest request = new OrderRequest("005930", OrderType.BUY, 5L);

        // when
        orderService.buy(1L, member, request);

        // then
        BigDecimal expected = new BigDecimal("1000000").subtract(new BigDecimal("75000").multiply(new BigDecimal("5")));
        assertThat(account.getBalance()).isEqualByComparingTo(expected); // 잔액 625,000
    }

    @Test
    @DisplayName("매수 실패 — 잔액 부족")
    void buy_fail_insufficientBalance() {
        // given
        account.withdraw(new BigDecimal("999999")); // 잔액 1원만 남김
        given(accountService.findWithLock(anyLong())).willReturn(account);
        given(stockService.findStockOrThrow("005930")).willReturn(stock);
        given(stockService.getCurrentPrice(anyString(), any())).willReturn(new BigDecimal("75000"));

        OrderRequest request = new OrderRequest("005930", OrderType.BUY, 1L);

        // when & then
        assertThatThrownBy(() -> orderService.buy(1L, member, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("매수 실패 — 계좌 소유자 불일치")
    void buy_fail_forbidden() {
        // given
        Member otherMember = Member.create("other@test.com", "pw", "남의회원", "010-1111-2222");
        ReflectionTestUtils.setField(otherMember, "id", 999L); // member.id=1L 과 다름
        given(accountService.findWithLock(anyLong())).willReturn(account); // account.member = member

        OrderRequest request = new OrderRequest("005930", OrderType.BUY, 1L);

        // when & then
        assertThatThrownBy(() -> orderService.buy(1L, otherMember, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ─── 매도 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("매도 성공 — 잔액 증가 및 보유 수량 감소")
    void sell_success() {
        // given
        Holding holding = Holding.of(account, stock, 10L, new BigDecimal("70000"));
        given(accountService.findWithLock(anyLong())).willReturn(account);
        given(stockService.findStockOrThrow("005930")).willReturn(stock);
        given(stockService.getCurrentPrice(anyString(), any())).willReturn(new BigDecimal("80000"));
        given(holdingRepository.findByAccountIdAndStockIdWithLock(anyLong(), anyLong()))
                .willReturn(Optional.of(holding));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        BigDecimal balanceBefore = account.getBalance();
        OrderRequest request = new OrderRequest("005930", OrderType.SELL, 3L);

        // when
        orderService.sell(1L, member, request);

        // then
        assertThat(account.getBalance())
                .isEqualByComparingTo(balanceBefore.add(new BigDecimal("80000").multiply(new BigDecimal("3"))));
        assertThat(holding.getQuantity()).isEqualTo(7L);
    }

    @Test
    @DisplayName("매도 실패 — 보유 수량 부족")
    void sell_fail_insufficientHoldings() {
        // given
        Holding holding = Holding.of(account, stock, 2L, new BigDecimal("70000"));
        given(accountService.findWithLock(anyLong())).willReturn(account);
        given(stockService.findStockOrThrow("005930")).willReturn(stock);
        given(stockService.getCurrentPrice(anyString(), any())).willReturn(new BigDecimal("80000"));
        given(holdingRepository.findByAccountIdAndStockIdWithLock(anyLong(), anyLong()))
                .willReturn(Optional.of(holding));

        OrderRequest request = new OrderRequest("005930", OrderType.SELL, 5L); // 5주 매도인데 보유 2주

        // when & then
        assertThatThrownBy(() -> orderService.sell(1L, member, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_HOLDINGS);
    }

    @Test
    @DisplayName("매도 실패 — 보유 종목 없음")
    void sell_fail_noHolding() {
        // given
        given(accountService.findWithLock(anyLong())).willReturn(account);
        given(stockService.findStockOrThrow("005930")).willReturn(stock);
        given(stockService.getCurrentPrice(anyString(), any())).willReturn(new BigDecimal("80000"));
        given(holdingRepository.findByAccountIdAndStockIdWithLock(anyLong(), anyLong()))
                .willReturn(Optional.empty());

        OrderRequest request = new OrderRequest("005930", OrderType.SELL, 1L);

        // when & then
        assertThatThrownBy(() -> orderService.sell(1L, member, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_HOLDINGS);
    }
}
