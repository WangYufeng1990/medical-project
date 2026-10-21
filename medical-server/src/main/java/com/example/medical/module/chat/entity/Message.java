package com.example.medical.module.chat.entity;

import com.example.medical.common.base.BaseEntity;
import com.example.medical.common.config.AesAttributeConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.SQLDelete;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "message")
@SQLDelete(sql = "UPDATE message SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class Message extends BaseEntity {

    @Column(name = "sender_id")
    private Long senderId;

    // STAFF (sys_user id) vs PATIENT (patient id) — the two ID spaces overlap,
    // so a bare Long cannot identify a party (see Post-Round 44 review R2-1).
    @Column(name = "sender_type", length = 10)
    private String senderType;

    @Column(name = "receiver_id")
    private Long receiverId;

    @Column(name = "receiver_type", length = 10)
    private String receiverType;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "content")
    private String content;

    @Column(name = "is_read")
    private Integer isRead;

    /**
     * The sender's idempotency key for this send (Round 54.3). Scoped to the sender's
     * typed identity by a unique index; null when the client did not supply one.
     */
    @Column(name = "client_message_id", length = 100)
    private String clientMessageId;
}
