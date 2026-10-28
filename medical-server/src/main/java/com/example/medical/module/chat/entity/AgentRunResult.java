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
 * What the worker produced for a run (Round 57.1): the routing decision, the draft, the citations
 * it relies on and the validator's reasons. Staff read this in the review queue; nothing here is
 * patient-visible until Round 57.2 publishes an approved message.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "agent_run_result")
@SQLDelete(sql = "UPDATE agent_run_result SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class AgentRunResult extends BaseEntity {

    @Column(name = "run_id")
    private Long runId;

    @Column(name = "intent", length = 32)
    private String intent;

    @Column(name = "risk", length = 16)
    private String risk;

    @Column(name = "requires_clinician")
    private Boolean requiresClinician;

    @Column(name = "draft", columnDefinition = "TEXT")
    private String draft;

    @Column(name = "validation_ok")
    private Boolean validationOk;

    @Column(name = "validation_kind", length = 32)
    private String validationKind;

    @Column(name = "validation_reasons", columnDefinition = "TEXT")
    private String validationReasons;

    @Column(name = "rejected_reasons", columnDefinition = "TEXT")
    private String rejectedReasons;

    @Column(name = "citations_json", columnDefinition = "TEXT")
    private String citationsJson;
}
