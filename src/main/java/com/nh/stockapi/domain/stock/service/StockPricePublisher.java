package com.nh.stockapi.domain.stock.service;

import com.nh.stockapi.domain.alert.service.PriceAlertService;
import com.nh.stockapi.domain.stock.dto.StockPriceData;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 갱신된 시세를 WebSocket 구독자에게 브로드캐스트한다.
 * 구독 토픽: /topic/prices/{ticker}
 */
@Component
@Profile("!test")
public class StockPricePublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final PriceAlertService priceAlertService;

    public StockPricePublisher(
            SimpMessagingTemplate messagingTemplate,
            @Lazy PriceAlertService priceAlertService) {
        this.messagingTemplate = messagingTemplate;
        this.priceAlertService = priceAlertService;
    }

    public void publish(String ticker, StockPriceData data) {
        messagingTemplate.convertAndSend("/topic/prices/" + ticker, data);
        priceAlertService.checkAndTrigger(ticker, data.price());
    }
}
