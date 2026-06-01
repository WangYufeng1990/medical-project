package com.example.medical.module.quality.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "quality_measure")
public class QualityMeasure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cms_id", length = 20, unique = true)
    private String cmsId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "denominator_query", length = 1000)
    private String denominatorQuery;

    @Column(name = "numerator_query", length = 1000)
    private String numeratorQuery;

    @Column(name = "exclusion_query", length = 1000)
    private String exclusionQuery;

    @Column(name = "report_period_months")
    private Integer reportPeriodMonths;
}
