package com.example.medical.module.chat.controller;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.Result;
import com.example.medical.module.chat.dto.SseTicketVO;
import com.example.medical.module.chat.event.NewMessageEvent;
import com.example.medical.module.chat.service.ChatService;
import com.example.medical.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
public class ChatSseController {

    // Key is type:id — patient ids and staff ids overlap, so a bare Long would
    // let a patient's emitter overwrite (or receive pushes meant for) a staff
    // user with the same id (Post-Round 44 review R2-1).
    static final Map<String, SseEmitter> EMITTERS = new ConcurrentHashMap<>();

    /**
     * Single-use tickets exchange the JWT for a random short-lived token, so the
     * JWT never appears in the subscribe URL (URLs land in access/proxy logs).
     */
    private static final Map<String, SseTicket> TICKETS = new ConcurrentHashMap<>();
    private static final long TICKET_TTL_MS = 30_000;

    @PostMapping("/api/v1/chat/sse-ticket")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PATIENT')")
    public Result<SseTicketVO> createTicket() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        LoginUser user = (LoginUser) auth.getPrincipal();
        String type = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PATIENT".equals(a.getAuthority()))
                ? ChatService.PATIENT : ChatService.STAFF;
        TICKETS.entrySet().removeIf(e -> e.getValue().expiresAt() < System.currentTimeMillis());
        String ticket = UUID.randomUUID().toString().replace("-", "");
        TICKETS.put(ticket, new SseTicket(type, user.getUserId(),
                System.currentTimeMillis() + TICKET_TTL_MS));
        return Result.ok(new SseTicketVO(ticket, (int) (TICKET_TTL_MS / 1000)));
    }

    @GetMapping(value = "/api/v1/chat/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam("ticket") String ticket) {
        SseTicket st = TICKETS.remove(ticket);
        if (st == null || st.expiresAt() < System.currentTimeMillis()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid or expired SSE ticket");
        }
        String key = st.type() + ":" + st.userId();
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        EMITTERS.put(key, emitter);
        emitter.onCompletion(() -> { EMITTERS.remove(key); log.debug("SSE completed: {}", key); });
        emitter.onTimeout(() -> { EMITTERS.remove(key); log.debug("SSE timeout: {}", key); });
        emitter.onError(e -> { EMITTERS.remove(key); log.debug("SSE error: {} {}", key, e.getMessage()); });

        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException e) {
            EMITTERS.remove(key);
            throw new RuntimeException("SSE send failed", e);
        }

        log.info("SSE subscriber connected: {}", key);
        return emitter;
    }

    public static void push(String receiverType, Long receiverId, NewMessageEvent event) {
        SseEmitter emitter = EMITTERS.get(receiverType + ":" + receiverId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event()
                    .name("new_message")
                    .data(event.message()));
        } catch (IOException e) {
            EMITTERS.remove(receiverType + ":" + receiverId);
        }
    }

    private record SseTicket(String type, Long userId, long expiresAt) {}
}
