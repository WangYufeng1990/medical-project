package com.example.medical.module.chat.event;

import com.example.medical.module.chat.dto.MessageVO;

public record NewMessageEvent(Long senderId, String senderType,
                              Long receiverId, String receiverType, MessageVO message) {}
