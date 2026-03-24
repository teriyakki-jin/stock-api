package com.nh.stockapi.domain.portfolio.service;

import com.nh.stockapi.common.exception.CustomException;
import com.nh.stockapi.common.exception.ErrorCode;
import com.nh.stockapi.domain.account.entity.Account;
import com.nh.stockapi.domain.account.service.AccountService;
import com.nh.stockapi.domain.member.entity.Member;
import com.nh.stockapi.domain.order.entity.Holding;
import com.nh.stockapi.domain.order.repository.HoldingRepository;
import com.nh.stockapi.domain.portfolio.dto.PortfolioSummaryResponse;
import com.nh.stockapi.domain.portfolio.dto.PortfolioSummaryResponse.HoldingDetail;
import com.nh.stockapi.domain.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final AccountService accountService;
    private final HoldingRepository holdingRepository;
    private final StockService stockService;

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getSummary(Long accountId, Member member) {
        Account account = accountService.findById(accountId);
        if (!account.getMember().getId().equals(member.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        List<Holding> holdings = holdingRepository.findByAccountIdWithStock(accountId);

        // currentPrice 조회
        record HoldingWithPrice(Holding h, BigDecimal price) {}
        List<HoldingWithPrice> withPrices = holdings.stream()
                .map(h -> new HoldingWithPrice(h,
                        stockService.getCurrentPrice(h.getStock().getTicker(), h.getStock().getBasePrice())))
                .toList();

        BigDecimal totalInvested = withPrices.stream()
                .map(hp -> hp.h().getAvgPrice().multiply(BigDecimal.valueOf(hp.h().getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal currentValue = withPrices.stream()
                .map(hp -> hp.price().multiply(BigDecimal.valueOf(hp.h().getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPnl = currentValue.subtract(totalInvested);

        double pnlRate = totalInvested.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                : totalPnl.divide(totalInvested, 6, RoundingMode.HALF_UP)
                          .multiply(BigDecimal.valueOf(100))
                          .doubleValue();

        List<HoldingDetail> details = withPrices.stream()
                .map(hp -> {
                    Holding h = hp.h();
                    BigDecimal price = hp.price();
                    BigDecimal evaluated = price.multiply(BigDecimal.valueOf(h.getQuantity()));

                    double profitRate = h.getAvgPrice().compareTo(BigDecimal.ZERO) == 0 ? 0.0
                            : price.subtract(h.getAvgPrice())
                                   .divide(h.getAvgPrice(), 6, RoundingMode.HALF_UP)
                                   .multiply(BigDecimal.valueOf(100))
                                   .doubleValue();

                    double allocationPct = currentValue.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                            : evaluated.divide(currentValue, 6, RoundingMode.HALF_UP)
                                       .multiply(BigDecimal.valueOf(100))
                                       .doubleValue();

                    return new HoldingDetail(
                            h.getId(),
                            h.getStock().getTicker(),
                            h.getStock().getName(),
                            h.getQuantity(),
                            h.getAvgPrice(),
                            price,
                            evaluated,
                            profitRate,
                            allocationPct
                    );
                })
                .toList();

        return new PortfolioSummaryResponse(totalInvested, currentValue, totalPnl, pnlRate, details);
    }
}
