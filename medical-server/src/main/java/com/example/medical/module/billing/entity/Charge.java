package com.example.medical.module.billing.entity;

import com.example.medical.common.base.BaseEntity;
import com.example.medical.common.config.AesAttributeConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "charge")
@EqualsAndHashCode(callSuper = true)
@SQLDelete(sql = "UPDATE charge SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class Charge extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "doctor_id")
    private Long doctorId;

    @Column(name = "cpt_codes", length = 200)
    private String cptCodes;

    @Column(name = "icd10_codes", length = 200)
    private String icd10Codes;

    @Column(length = 10)
    private Integer units = 1;

    @Column(name = "charge_amount")
    private BigDecimal chargeAmount;

    @Column(name = "visit_type", length = 30)
    private String visitType;

    @Column(length = 20, nullable = false)
    private String status = "DRAFT";

    @Column(name = "bill_id")
    private Long billId;

    @Convert(converter = AesAttributeConverter.class)
    @Column
    private String notes;
}
