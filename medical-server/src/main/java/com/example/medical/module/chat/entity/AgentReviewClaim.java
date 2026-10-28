package com.example.medical.module.chat.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** One reviewer has taken a run (Round 57.1); the unique key is the run id. */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "agent_review_claim")
@SQLDelete(sql = "UPDATE agent_review_claim SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class AgentReviewClaim extends BaseEntity {

    @Column(name = "run_id")
    private Long runId;

    @Column(name = "staff_id")
    private Long staffId;
}
