package com.example.medical.module.prescription.entity;

import com.example.medical.common.base.BaseEntity;
import com.example.medical.common.config.AesAttributeConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "prescription")
@SQLDelete(sql = "UPDATE prescription SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class Prescription extends BaseEntity {

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "doctor_id")
    private Long doctorId;

    @Column(name = "diagnosis")
    private String diagnosis;

    @Column(name = "icd10_codes", length = 500)
    private String icd10Codes;

    @Column(name = "prescription_date")
    private LocalDate prescriptionDate;

    @Column(name = "prescription_type", length = 20)
    private String prescriptionType;

    @Column(name = "rx_status", length = 20)
    private String rxStatus;

    @Column(name = "prescriber_npi", length = 10)
    private String prescriberNpi;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "dea_number")
    private String deaNumber;

    @Column(name = "controlled_schedule", length = 5)
    private String controlledSchedule;

    @Column(name = "pharmacy_name", length = 100)
    private String pharmacyName;

    @Column(name = "pharmacy_phone", length = 20)
    private String pharmacyPhone;

    @Column(name = "pharmacy_npi", length = 10)
    private String pharmacyNpi;
}
