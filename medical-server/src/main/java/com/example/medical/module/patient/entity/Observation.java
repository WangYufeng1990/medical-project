package com.example.medical.module.patient.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "observation")
public class Observation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) createTime = LocalDateTime.now();
    }
}
