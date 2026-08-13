package com.example.medical.module.chat.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConversationVO {

    private Long partnerId;
    private String partnerType;
    private String partnerName;
    private String lastMessage;
    private LocalDateTime lastMessageTime;
    private int unreadCount;

    public static ConversationVO of(Long partnerId, String partnerType, String partnerName,
                                     String lastMessage, LocalDateTime lastMessageTime,
                                     int unreadCount) {
        return new ConversationVO(partnerId, partnerType, partnerName, lastMessage, lastMessageTime, unreadCount);
    }
}
