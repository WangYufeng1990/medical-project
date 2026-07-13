package com.example.medical.module.patient.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "allergy_entry")
@SQLDelete(sql = "UPDATE allergy_entry SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class AllergyEntry extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "allergen", length = 200, nullable = false)
    private String allergen;

    @Column(name = "reaction", length = 200)
    private String reaction;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private java.time.LocalDateTime resolvedAt;
}
