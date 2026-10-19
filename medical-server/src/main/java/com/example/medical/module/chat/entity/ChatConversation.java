package com.example.medical.module.chat.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * A thread between two typed parties, stored so that listing conversations is a
 * query and pagination is real (Round 54.2).
 * <p>
 * The pair is canonical: {@code (partyAType, partyAId) < (partyBType, partyBId)},
 * enforced by a unique key, so patient 103 ↔ staff 103 and staff 103 ↔ patient 103
 * cannot become two rows. {@code lastMessageTime} plus {@code id} is the list
 * ordering; {@code revision} counts the messages recorded on the thread.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "chat_conversation")
@SQLDelete(sql = "UPDATE chat_conversation SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class ChatConversation extends BaseEntity {

    @Column(name = "party_a_type", length = 10)
    private String partyAType;

    @Column(name = "party_a_id")
    private Long partyAId;

    @Column(name = "party_b_type", length = 10)
    private String partyBType;

    @Column(name = "party_b_id")
    private Long partyBId;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_time")
    private LocalDateTime lastMessageTime;

    @Column(name = "message_count")
    private Integer messageCount;

    @Column(name = "revision")
    private Long revision;
}
