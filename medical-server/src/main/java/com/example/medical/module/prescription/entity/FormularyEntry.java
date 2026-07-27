package com.example.medical.module.prescription.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "formulary_entry")
public class FormularyEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rxnorm_code", length = 20, nullable = false)
    private String rxnormCode;

    @Column(name = "drug_name", length = 200, nullable = false)
    private String drugName;

    @Column(name = "insurance_payer", length = 200, nullable = false)
    private String insurancePayer;

    @Column(length = 20, nullable = false)
    private String tier;

    @Column(name = "prior_auth_required")
    private Boolean priorAuthRequired;

    @Column(name = "step_therapy_required")
    private Boolean stepTherapyRequired;

    @Column(length = 200)
    private String alternatives;
}
