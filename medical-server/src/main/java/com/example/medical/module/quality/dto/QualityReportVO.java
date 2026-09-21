package com.example.medical.module.quality.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * eCQM report for one measure — the same ten keys whether it was read back or
 * just calculated. {@code title} is always the measure's own title;
 * {@code performanceTarget} carries the human-readable target description.
 */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QualityReportVO {

    private String cmsId;
    private String title;
    private Integer reportPeriodMonths;
    private Long denominator;
    private Long exclusions;
    private Long eligibleDenominator;
    private Long numerator;
    private Double performanceRate;
    private String performanceTarget;
    private LocalDateTime calculatedAt;

    public static QualityReportVO of(String cmsId, String title, Integer reportPeriodMonths,
                                     Long denominator, Long exclusions, Long eligibleDenominator,
                                     Long numerator, Double performanceRate, String performanceTarget,
                                     LocalDateTime calculatedAt) {
        return new QualityReportVO(cmsId, title, reportPeriodMonths, denominator, exclusions,
                eligibleDenominator, numerator, performanceRate, performanceTarget, calculatedAt);
    }
}
