package com.example.medical.module.patient.entity;

import com.example.medical.common.base.BaseEntity;
import com.example.medical.common.config.AesAttributeConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "medical_history_entry")
@SQLDelete(sql = "UPDATE medical_history_entry SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class MedicalHistoryEntry extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "recorded_by")
    private Long recordedBy;
}
