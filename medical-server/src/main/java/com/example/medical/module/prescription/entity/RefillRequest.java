package com.example.medical.module.prescription.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "refill_request")
@EqualsAndHashCode(callSuper = true)
@SQLDelete(sql = "UPDATE refill_request SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class RefillRequest extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Column(length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(length = 500)
    private String reason;

    @Column(name = "review_notes", length = 500)
    private String reviewNotes;
}
