package com.example.medical.module.prescription.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "drug_allergy_class")
public class DrugAllergyClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drug_rxnorm_code", length = 20)
    private String drugRxnormCode;

    @Column(name = "allergy_class", length = 100)
    private String allergyClass;

    @Column(name = "cross_reactive_codes", length = 500)
    private String crossReactiveCodes;
}
