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
 * One durable dispatch job, written in the same transaction as the message it is about
 * (Round 55.2).
 * <p>
 * The row is the queue: a lease keeps two dispatchers from sending the same job, the
 * attempt counter bounds retries with backoff, and the deadline bounds the whole thing so
 * a stuck job ends in a state instead of living forever. {@code executionGeneration}
 * increments on every claim, which is what a later round uses to fence a stale worker.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "agent_run")
@SQLDelete(sql = "UPDATE agent_run SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class AgentRun extends BaseEntity {

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "conversation_revision")
    private Long conversationRevision;

    @Column(name = "state", length = 20)
    private String state;

    @Column(name = "attempt")
    private Integer attempt;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    @Column(name = "lease_owner", length = 64)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "execution_generation")
    private Long executionGeneration;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
