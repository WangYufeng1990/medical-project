package com.example.medical.module.quality.service;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.quality.entity.QualityMeasure;
import com.example.medical.module.quality.repository.QualityMeasureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QualityMeasureService {

    private final QualityMeasureRepository qualityMeasureRepository;
    private final JdbcTemplate jdbcTemplate;

    public List<QualityMeasure> listMeasures() {
        return qualityMeasureRepository.findAll();
    }

    public Map<String, Object> calculateReport(String cmsId) {
        QualityMeasure measure = qualityMeasureRepository.findByCmsId(cmsId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND,
                        "Measure not found: " + cmsId));

        long denominator = executeCount(measure.getDenominatorQuery());
        long numerator = denominator > 0 ? executeCount(measure.getNumeratorQuery()) : 0;
        long exclusions = measure.getExclusionQuery() != null
                ? executeCount(measure.getExclusionQuery()) : 0;

        long eligibleDenominator = denominator - exclusions;
        double performance = eligibleDenominator > 0
                ? (double) numerator / eligibleDenominator * 100.0 : 0.0;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("cmsId", measure.getCmsId());
        report.put("title", measure.getTitle());
        report.put("reportPeriodMonths", measure.getReportPeriodMonths());
        report.put("denominator", denominator);
        report.put("exclusions", exclusions);
        report.put("eligibleDenominator", eligibleDenominator);
        report.put("numerator", numerator);
        report.put("performanceRate", Math.round(performance * 10.0) / 10.0);
        report.put("performanceTarget", getTarget(cmsId));
        return report;
    }

    private long executeCount(String query) {
        if (query == null || query.isBlank()) return 0;
        try {
            Long result = jdbcTemplate.queryForObject(query, Long.class);
            return result != null ? result : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String getTarget(String cmsId) {
        return switch (cmsId) {
            case "CMS122v11" -> "HbA1c < 9% in diabetic patients. Target: ≥ 70%";
            case "CMS125v11" -> "Breast cancer screening in women 50-74. Target: ≥ 70%";
            case "CMS165v11" -> "Blood pressure < 140/90 in hypertensive patients. Target: ≥ 60%";
            default -> "Target not defined";
        };
    }
}
