package com.example.medical.module.patient.entity;

import com.example.medical.common.base.BaseEntity;
import com.example.medical.common.config.AesAttributeConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "problem")
@EqualsAndHashCode(callSuper = true)
@SQLDelete(sql = "UPDATE problem SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class Problem extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "snomed_code", length = 20)
    private String snomedCode;

    @Column(name = "snomed_display", length = 200)
    private String snomedDisplay;

    @Column(name = "icd10_code", length = 10)
    private String icd10Code;

    @Column(name = "onset_date")
    private LocalDate onsetDate;

    @Column(name = "resolution_date")
    private LocalDate resolutionDate;

    @Column(length = 20, nullable = false)
    private String status = "ACTIVE";

    @Column(length = 10)
    private String severity;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Convert(converter = AesAttributeConverter.class)
    @Column
    private String notes;
}
