package com.example.medical.module.chat.entity;

import com.example.medical.common.base.BaseEntity;
import com.example.medical.common.config.AesAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * One reviewer decision per run (Round 57.2): who decided, what was published, and why a draft was
 * refused. The original draft lives in {@code agent_run_result}; this row keeps the final text so an
 * edit cannot be lost.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "agent_review_decision")
@SQLDelete(sql = "UPDATE agent_review_decision SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class AgentReviewDecision extends BaseEntity {

    @Column(name = "run_id")
    private Long runId;

    @Column(name = "decision", length = 16)
    private String decision;

    // The published reply is patient data: it is encrypted at rest like message.content, so the
    // decision table cannot become a plaintext copy of the thread (Round 57.2 review).
    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "final_text", columnDefinition = "TEXT")
    private String finalText;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "published_message_id")
    private Long publishedMessageId;

    @Column(name = "edited")
    private Boolean edited;
}
