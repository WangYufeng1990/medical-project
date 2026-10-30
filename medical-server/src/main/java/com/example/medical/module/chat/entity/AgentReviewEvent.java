package com.example.medical.module.chat.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * One review action, successful or refused (Round 57.3). The decision table records decisions;
 * this records the attempts, including the ones that were denied, so "who tried to approve what"
 * is answerable.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "agent_review_event")
@SQLDelete(sql = "UPDATE agent_review_event SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class AgentReviewEvent extends BaseEntity {

    @Column(name = "run_id")
    private Long runId;

    @Column(name = "action", length = 16)
    private String action;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;
}
