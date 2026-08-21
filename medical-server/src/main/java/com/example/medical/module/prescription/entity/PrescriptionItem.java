package com.example.medical.module.prescription.entity;

import com.example.medical.common.base.BaseEntity;
import com.example.medical.common.config.AesAttributeConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "prescription_item")
@SQLDelete(sql = "UPDATE prescription_item SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class PrescriptionItem extends BaseEntity {

    @Column(name = "prescription_id")
    private Long prescriptionId;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "drug_name", length = 1000)
    private String drugName;

    @Column(name = "ndc_code", length = 20)
    private String ndcCode;

    @Column(name = "rxnorm_code", length = 20)
    private String rxnormCode;

    @Column(name = "specification")
    private String specification;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "dosage", length = 1000)
    private String dosage;

    @Column(name = "route", length = 10)
    private String route;

    @Column(name = "frequency")
    private String frequency;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "sig", length = 1000)
    private String sig;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "days_supply")
    private Integer daysSupply;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "refills")
    private Integer refills;

    @Column(name = "daw")
    private Integer daw;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "notes", length = 1000)
    private String notes;
}
