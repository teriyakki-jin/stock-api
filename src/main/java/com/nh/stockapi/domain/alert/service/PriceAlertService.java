package com.nh.stockapi.domain.alert.service;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.account.repository.AccountRepository;
import com.nh.stockapi.domain.alert.dto.PriceAlertRequest;
import com.nh.stockapi.domain.alert.dto.PriceAlertResponse;
import com.nh.stockapi.domain.alert.entity.PriceAlert;
import com.nh.stockapi.domain.alert.repository.PriceAlertRepository;
import com.nh.stockapi.domain.member.entity.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAlertService {

    private final PriceAlertRepository alertRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public PriceAlertResponse create(Long accountId, PriceAlertRequest req, Member member) {
        Account account = findAccountOfMember(accountId, member);
        PriceAlert alert = PriceAlert.create(account, req.ticker(), req.targetPrice(), req.condition());
        return PriceAlertResponse.from(alertRepository.save(alert));
    }

    @Transactional(readOnly = true)
    public List<PriceAlertResponse> list(Long accountId, Member member) {
        validateOwnership(accountId, member);
        return alertRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
                .stream().map(PriceAlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PriceAlertResponse> listUnacknowledged(Long accountId, Member member) {
        validateOwnership(accountId, member);
        return alertRepository.findUnacknowledgedByAccountId(accountId)
                .stream().map(PriceAlertResponse::from).toList();
    }

    @Transactional
    public void delete(Long accountId, Long alertId, Member member) {
        validateOwnership(accountId, member);
        PriceAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new CustomException(ErrorCode.ALERT_NOT_FOUND));
        if (!alert.getAccount().getId().equals(accountId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        alertRepository.delete(alert);
    }

    @Transactional
    public void acknowledge(Long accountId, Long alertId, Member member) {
        validateOwnership(accountId, member);
        PriceAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new CustomException(ErrorCode.ALERT_NOT_FOUND));
        if (!alert.getAccount().getId().equals(accountId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        alert.acknowledge();
    }

    private Account findAccountOfMember(Long accountId, Member member) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!account.getMember().getId().equals(member.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return account;
    }

    private void validateOwnership(Long accountId, Member member) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!account.getMember().getId().equals(member.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    /** StockPricePublisher에서 가격 업데이트 시 호출 */
    @Transactional
    public void checkAndTrigger(String ticker, BigDecimal currentPrice) {
        List<PriceAlert> active = alertRepository.findActiveByTicker(ticker);
        for (PriceAlert alert : active) {
            if (alert.checkAndTrigger(currentPrice)) {
                log.info("[알림 발동] 계좌={} 종목={} 조건={} 목표가={} 현재가={}",
                    alert.getAccount().getId(), ticker, alert.getCondition(),
                    alert.getTargetPrice(), currentPrice);
            }
        }
    }
}
