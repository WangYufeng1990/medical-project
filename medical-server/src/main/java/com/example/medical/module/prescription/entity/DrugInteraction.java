package com.example.medical.module.prescription.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "drug_interaction")
public class DrugInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drug_a_rxnorm", length = 20)
    private String drugARxnorm;

    @Column(name = "drug_b_rxnorm", length = 20)
    private String drugBRxnorm;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "mechanism", length = 200)
    private String mechanism;

    @Column(name = "recommendation", length = 500)
    private String recommendation;
}
