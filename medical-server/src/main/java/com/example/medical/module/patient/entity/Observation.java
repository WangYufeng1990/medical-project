package com.example.medical.module.patient.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "observation")
@SQLDelete(sql = "UPDATE observation SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class Observation extends BaseEntity {

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "loinc_code", length = 20)
    private String loincCode;

    @Column(name = "loinc_display", length = 200)
    private String loincDisplay;

    @Column(name = "obs_value", length = 50)
    private String obsValue;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "reference_range", length = 50)
    private String referenceRange;

    @Column(name = "abnormal_flag", length = 1)
    private String abnormalFlag;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "source_message_id", length = 100)
    private String sourceMessageId;

    @Column(name = "effective_date")
    private LocalDateTime effectiveDate;
}
