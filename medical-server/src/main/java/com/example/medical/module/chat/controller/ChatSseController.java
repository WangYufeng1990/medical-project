package com.example.medical.module.chat.controller;

import com.example.medical.module.chat.event.NewMessageEvent;
import com.example.medical.module.chat.dto.MessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
public class ChatSseController {

    static final Map<Long, SseEmitter> EMITTERS = new ConcurrentHashMap<>();

    @GetMapping(value = "/api/v1/chat/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal Jwt jwt) {
        Long userId = extractUserId(jwt);
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        EMITTERS.put(userId, emitter);
        emitter.onCompletion(() -> { EMITTERS.remove(userId); log.debug("SSE completed: {}", userId); });
        emitter.onTimeout(() -> { EMITTERS.remove(userId); log.debug("SSE timeout: {}", userId); });
        emitter.onError(e -> { EMITTERS.remove(userId); log.debug("SSE error: {} {}", userId, e.getMessage()); });

        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException e) {
            EMITTERS.remove(userId);
            throw new RuntimeException("SSE send failed", e);
        }

        log.info("SSE subscriber connected: userId={}", userId);
        return emitter;
    }

    public static void push(Long receiverId, NewMessageEvent event) {
        SseEmitter emitter = EMITTERS.get(receiverId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name("new_message")
                    .data(event.message()));
        } catch (IOException e) {
            EMITTERS.remove(receiverId);
        }
    }

    private Long extractUserId(Jwt jwt) {
        String uid = jwt.getClaimAsString("uid");
        if (uid != null) {
            try { return Long.valueOf(uid); } catch (NumberFormatException ignored) {}
        }
        String jti = jwt.getId();
        if (jti != null) {
            try { return Long.valueOf(jti); } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }
}
