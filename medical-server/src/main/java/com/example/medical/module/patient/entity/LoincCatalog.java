package com.example.medical.module.patient.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "loinc_catalog")
public class LoincCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loinc_code", length = 20, unique = true)
    private String loincCode;

    @Column(name = "display", length = 200)
    private String display;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "ref_range_low", length = 20)
    private String refRangeLow;

    @Column(name = "ref_range_high", length = 20)
    private String refRangeHigh;

    @Column(name = "panel_parent_code", length = 20)
    private String panelParentCode;
}
