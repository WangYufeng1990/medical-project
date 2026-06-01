package com.example.medical.module.patient.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "consent")
@SQLDelete(sql = "UPDATE consent SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class Consent extends BaseEntity {

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "consent_type", length = 30)
    private String consentType;

    @Column(name = "scope", length = 100)
    private String scope;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "policy_uri", length = 255)
    private String policyUri;

    @Column(name = "provision_period_start")
    private LocalDate provisionPeriodStart;

    @Column(name = "provision_period_end")
    private LocalDate provisionPeriodEnd;

    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "consent_date")
    private LocalDate consentDate;
}
