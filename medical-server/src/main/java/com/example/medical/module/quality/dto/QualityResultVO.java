package com.example.medical.module.quality.dto;

import com.example.medical.module.quality.entity.QualityResult;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QualityResultVO {

    private Long id;
    private String cmsId;
    private Long denominator;
    private Long exclusions;
    private Long eligibleDenominator;
    private Long numerator;
    private Double performanceRate;
    private String performanceTarget;
    private Integer reportPeriodMonths;
    private LocalDateTime calculatedAt;

    public static QualityResultVO fromEntity(QualityResult r) {
        QualityResultVO vo = new QualityResultVO();
        vo.setId(r.getId());
        vo.setCmsId(r.getCmsId());
        vo.setDenominator(r.getDenominator());
        vo.setExclusions(r.getExclusions());
        vo.setEligibleDenominator(r.getEligibleDenominator());
        vo.setNumerator(r.getNumerator());
        vo.setPerformanceRate(r.getPerformanceRate());
        vo.setPerformanceTarget(r.getPerformanceTarget());
        vo.setReportPeriodMonths(r.getReportPeriodMonths());
        vo.setCalculatedAt(r.getCalculatedAt());
        return vo;
    }
}
