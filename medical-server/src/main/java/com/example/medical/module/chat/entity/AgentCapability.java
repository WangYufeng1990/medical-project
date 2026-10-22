package com.example.medical.module.chat.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * The stored half of an execution capability (Round 55.3).
 * <p>
 * Only a SHA-256 digest of the token is kept. It is bound to one run and one execution
 * generation, so a re-dispatch — which increments the generation — cannot be served by the
 * capability the previous attempt was given.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "agent_capability")
@SQLDelete(sql = "UPDATE agent_capability SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class AgentCapability extends BaseEntity {

    @Column(name = "run_id")
    private Long runId;

    @Column(name = "digest", length = 64)
    private String digest;

    @Column(name = "generation")
    private Long generation;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
