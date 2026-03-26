package com.nh.stockapi.domain.alert.service;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.account.repository.AccountRepository;
import com.nh.stockapi.domain.alert.dto.PriceAlertRequest;
import com.nh.stockapi.domain.alert.dto.PriceAlertResponse;
import com.nh.stockapi.domain.alert.entity.AlertCondition;
import com.nh.stockapi.domain.alert.entity.PriceAlert;
import com.nh.stockapi.domain.alert.repository.PriceAlertRepository;
import com.nh.stockapi.domain.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PriceAlertService 단위 테스트")
class PriceAlertServiceTest {

    @InjectMocks PriceAlertService alertService;
    @Mock PriceAlertRepository alertRepository;
    @Mock AccountRepository accountRepository;

    Member member;
    Member otherMember;
    Account account;

    @BeforeEach
    void setUp() {
        member = Member.create("test@test.com", "encoded-pw", "테스터", "010-0000-0000");
        ReflectionTestUtils.setField(member, "id", 1L);

        otherMember = Member.create("other@test.com", "encoded-pw", "타인", "010-1111-2222");
        ReflectionTestUtils.setField(otherMember, "id", 99L);

        account = Account.open(member);
        ReflectionTestUtils.setField(account, "id", 100L);
    }

    // ── 생성 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("알림 등록 성공")
    void create_success() {
        // given
        PriceAlertRequest req = new PriceAlertRequest("005930", new BigDecimal("80000"), AlertCondition.GTE);
        given(accountRepository.findById(100L)).willReturn(Optional.of(account));
        given(alertRepository.save(any(PriceAlert.class))).willAnswer(inv -> {
            PriceAlert a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 1L);
            return a;
        });

        // when
        PriceAlertResponse response = alertService.create(100L, req, member);

        // then
        assertThat(response.ticker()).isEqualTo("005930");
        assertThat(response.targetPrice()).isEqualByComparingTo(new BigDecimal("80000"));
        assertThat(response.condition()).isEqualTo(AlertCondition.GTE);
        assertThat(response.active()).isTrue();
        then(alertRepository).should().save(any(PriceAlert.class));
    }

    @Test
    @DisplayName("알림 등록 실패 — 타인 계좌 접근")
    void create_fail_forbidden() {
        // given
        PriceAlertRequest req = new PriceAlertRequest("005930", new BigDecimal("80000"), AlertCondition.GTE);
        given(accountRepository.findById(100L)).willReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> alertService.create(100L, req, otherMember))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("알림 등록 실패 — 계좌 없음")
    void create_fail_accountNotFound() {
        // given
        PriceAlertRequest req = new PriceAlertRequest("005930", new BigDecimal("80000"), AlertCondition.GTE);
        given(accountRepository.findById(anyLong())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> alertService.create(999L, req, member))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    // ── 목록 조회 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("알림 목록 조회 성공")
    void list_success() {
        // given
        PriceAlert alert = PriceAlert.create(account, "005930", new BigDecimal("80000"), AlertCondition.GTE);
        ReflectionTestUtils.setField(alert, "id", 1L);
        given(accountRepository.findById(100L)).willReturn(Optional.of(account));
        given(alertRepository.findByAccountIdOrderByCreatedAtDesc(100L)).willReturn(List.of(alert));

        // when
        List<PriceAlertResponse> result = alertService.list(100L, member);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).ticker()).isEqualTo("005930");
    }

    @Test
    @DisplayName("알림 목록 조회 실패 — 타인 계좌")
    void list_fail_forbidden() {
        given(accountRepository.findById(100L)).willReturn(Optional.of(account));

        assertThatThrownBy(() -> alertService.list(100L, otherMember))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ── 삭제 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("알림 삭제 성공")
    void delete_success() {
        // given
        PriceAlert alert = PriceAlert.create(account, "005930", new BigDecimal("80000"), AlertCondition.GTE);
        ReflectionTestUtils.setField(alert, "id", 1L);
        given(accountRepository.findById(100L)).willReturn(Optional.of(account));
        given(alertRepository.findById(1L)).willReturn(Optional.of(alert));

        // when
        alertService.delete(100L, 1L, member);

        // then
        then(alertRepository).should().delete(alert);
    }

    @Test
    @DisplayName("알림 삭제 실패 — 알림 없음")
    void delete_fail_alertNotFound() {
        given(accountRepository.findById(100L)).willReturn(Optional.of(account));
        given(alertRepository.findById(anyLong())).willReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.delete(100L, 999L, member))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALERT_NOT_FOUND);
    }

    @Test
    @DisplayName("알림 삭제 실패 — 타인 알림 삭제 시도")
    void delete_fail_alertBelongsToOther() {
        // given: 다른 계좌의 알림을 targetAlert으로 설정
        Account otherAccount = Account.open(otherMember);
        ReflectionTestUtils.setField(otherAccount, "id", 200L);
        PriceAlert otherAlert = PriceAlert.create(otherAccount, "000660", new BigDecimal("200000"), AlertCondition.LTE);
        ReflectionTestUtils.setField(otherAlert, "id", 99L);

        given(accountRepository.findById(100L)).willReturn(Optional.of(account));
        given(alertRepository.findById(99L)).willReturn(Optional.of(otherAlert));

        assertThatThrownBy(() -> alertService.delete(100L, 99L, member))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ── 확인 처리 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("알림 확인 처리 성공")
    void acknowledge_success() {
        // given: 발동된 알림
        PriceAlert alert = PriceAlert.create(account, "005930", new BigDecimal("70000"), AlertCondition.LTE);
        ReflectionTestUtils.setField(alert, "id", 1L);
        alert.checkAndTrigger(new BigDecimal("68000")); // 발동

        given(accountRepository.findById(100L)).willReturn(Optional.of(account));
        given(alertRepository.findById(1L)).willReturn(Optional.of(alert));

        // when
        alertService.acknowledge(100L, 1L, member);

        // then
        assertThat(alert.isAcknowledged()).isTrue();
    }

    // ── checkAndTrigger ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GTE 조건 발동 — 현재가가 목표가 이상일 때")
    void checkAndTrigger_gte_hit() {
        // given
        PriceAlert alert = PriceAlert.create(account, "005930", new BigDecimal("80000"), AlertCondition.GTE);
        given(alertRepository.findActiveByTicker("005930")).willReturn(List.of(alert));

        // when
        alertService.checkAndTrigger("005930", new BigDecimal("80000"));

        // then
        assertThat(alert.isTriggered()).isTrue();
        assertThat(alert.isActive()).isFalse();
    }

    @Test
    @DisplayName("GTE 조건 미발동 — 현재가가 목표가 미만일 때")
    void checkAndTrigger_gte_miss() {
        // given
        PriceAlert alert = PriceAlert.create(account, "005930", new BigDecimal("80000"), AlertCondition.GTE);
        given(alertRepository.findActiveByTicker("005930")).willReturn(List.of(alert));

        // when
        alertService.checkAndTrigger("005930", new BigDecimal("79999"));

        // then
        assertThat(alert.isTriggered()).isFalse();
        assertThat(alert.isActive()).isTrue();
    }

    @Test
    @DisplayName("LTE 조건 발동 — 현재가가 목표가 이하일 때")
    void checkAndTrigger_lte_hit() {
        // given
        PriceAlert alert = PriceAlert.create(account, "000660", new BigDecimal("200000"), AlertCondition.LTE);
        given(alertRepository.findActiveByTicker("000660")).willReturn(List.of(alert));

        // when
        alertService.checkAndTrigger("000660", new BigDecimal("199999"));

        // then
        assertThat(alert.isTriggered()).isTrue();
        assertThat(alert.isActive()).isFalse();
    }

    @Test
    @DisplayName("이미 발동된 알림은 재발동하지 않음")
    void checkAndTrigger_alreadyTriggered() {
        // given: 이미 발동된 알림은 findActiveByTicker에서 제외 (active=false)
        // 서비스 레이어에서 active=true 인 것만 조회
        given(alertRepository.findActiveByTicker("005930")).willReturn(List.of());

        // when
        alertService.checkAndTrigger("005930", new BigDecimal("90000"));

        // then: 저장 호출 없음
        then(alertRepository).should(never()).save(any());
    }
}
