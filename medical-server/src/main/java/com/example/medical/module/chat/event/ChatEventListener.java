package com.example.medical.module.chat.event;

import com.example.medical.module.chat.controller.ChatSseController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventListener {

    @Async("auditExecutor")
    @EventListener
    public void onNewMessage(NewMessageEvent event) {
        ChatSseController.push(event.receiverType(), event.receiverId(), event);
        log.debug("SSE push: sender={}:{} receiver={}:{}", event.senderType(), event.senderId(),
                event.receiverType(), event.receiverId());
    }
}
