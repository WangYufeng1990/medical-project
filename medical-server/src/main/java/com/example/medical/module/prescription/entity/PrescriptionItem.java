package com.example.medical.module.prescription.entity;

import com.example.medical.common.base.BaseEntity;
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
@SQLDelete(sql = "UPDATE prescription_item SET is_deleted = 1 WHERE id = ?")
@SQLRestriction("is_deleted = 0")
public class PrescriptionItem extends BaseEntity {

    @Column(name = "prescription_id")
    private Long prescriptionId;

    @Column(name = "drug_name")
    private String drugName;

    @Column(name = "ndc_code", length = 20)
    private String ndcCode;

    @Column(name = "rxnorm_code", length = 20)
    private String rxnormCode;

    @Column(name = "specification")
    private String specification;

    @Column(name = "dosage")
    private String dosage;

    @Column(name = "route", length = 10)
    private String route;

    @Column(name = "frequency")
    private String frequency;

    @Column(name = "sig")
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

    @Column(name = "notes")
    private String notes;
}
