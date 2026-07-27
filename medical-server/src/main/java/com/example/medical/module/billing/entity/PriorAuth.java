package com.example.medical.module.billing.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "prior_auth")
@EqualsAndHashCode(callSuper = true)
@SQLDelete(sql = "UPDATE prior_auth SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class PriorAuth extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(length = 20)
    private String authType;

    @Column(name = "item_name", length = 200)
    private String itemName;

    @Column(name = "item_code", length = 20)
    private String itemCode;

    @Column(name = "insurance_payer", length = 200)
    private String insurancePayer;

    @Column(length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "requested_at", nullable = false)
    private LocalDate requestedAt;

    @Column(name = "resolved_at")
    private LocalDate resolvedAt;

    @Column(name = "auth_number", length = 50)
    private String authNumber;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(length = 500)
    private String notes;
}
