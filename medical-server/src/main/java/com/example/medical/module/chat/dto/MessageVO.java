package com.example.medical.module.chat.dto;

import com.example.medical.module.chat.entity.Message;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MessageVO {

    private Long id;
    private Long senderId;
    private Long receiverId;
    private String content;
    private boolean isRead;
    private LocalDateTime createTime;

    public static MessageVO fromEntity(Message m) {
        return new MessageVO(m.getId(), m.getSenderId(), m.getReceiverId(),
                m.getContent(), m.getIsRead() != null && m.getIsRead() == 1,
                m.getCreateTime());
    }
}
