package com.example.medical.module.quality.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "quality_result")
public class QualityResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cms_id", length = 20, nullable = false)
    private String cmsId;

    @Column(name = "denominator", nullable = false)
    private Long denominator;

    @Column(name = "exclusions", nullable = false)
    private Long exclusions;

    @Column(name = "eligible_denominator", nullable = false)
    private Long eligibleDenominator;

    @Column(name = "numerator", nullable = false)
    private Long numerator;

    @Column(name = "performance_rate", nullable = false)
    private Double performanceRate;

    @Column(name = "performance_target", length = 200)
    private String performanceTarget;

    @Column(name = "report_period_months")
    private Integer reportPeriodMonths;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @PrePersist
    protected void onCreate() {
        if (calculatedAt == null) calculatedAt = LocalDateTime.now();
    }
}
