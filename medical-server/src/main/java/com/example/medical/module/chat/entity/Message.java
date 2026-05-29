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
@SQLDelete(sql = "UPDATE message SET is_deleted = 1 WHERE id = ?")
@SQLRestriction("is_deleted = 0")
public class Message extends BaseEntity {

    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "receiver_id")
    private Long receiverId;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "content")
    private String content;

    @Column(name = "is_read")
    private Integer isRead;
}
