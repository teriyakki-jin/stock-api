package com.nh.stockapi.domain.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE(Server-Sent Events) 연결 관리 서비스.
 * 키: 회원 이메일 → SseEmitter
 */
@Slf4j
@Service
public class SseEmitterService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String email) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(email, emitter);
        emitter.onCompletion(() -> emitters.remove(email));
        emitter.onTimeout(() -> emitters.remove(email));
        emitter.onError(e -> emitters.remove(email));

        // 최초 연결 확인 이벤트
        try {
            emitter.send(SseEmitter.event().name("connected").data("OK"));
        } catch (IOException e) {
            emitters.remove(email);
        }
        return emitter;
    }

    public void send(String email, Object data) {
        SseEmitter emitter = emitters.get(email);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("order-fill").data(data));
        } catch (IOException e) {
            log.warn("[SSE] 전송 실패: {}", email);
            emitters.remove(email);
        }
    }

    public boolean isConnected(String email) {
        return emitters.containsKey(email);
    }
}
