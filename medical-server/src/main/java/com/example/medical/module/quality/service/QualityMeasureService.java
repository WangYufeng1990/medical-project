package com.example.medical.module.quality.service;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.quality.entity.QualityMeasure;
import com.example.medical.module.quality.entity.QualityResult;
import com.example.medical.module.quality.repository.QualityMeasureRepository;
import com.example.medical.module.quality.repository.QualityResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityMeasureService {

    private final QualityMeasureRepository qualityMeasureRepository;
    private final QualityResultRepository qualityResultRepository;
    private final JdbcTemplate jdbcTemplate;

    public List<QualityMeasure> listMeasures() {
        return qualityMeasureRepository.findAll();
    }

    public Map<String, Object> getReport(String cmsId) {
        QualityMeasure measure = qualityMeasureRepository.findByCmsId(cmsId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND,
                        "Measure not found: " + cmsId));

        QualityResult latest = qualityResultRepository
                .findTopByCmsIdOrderByCalculatedAtDesc(cmsId).orElse(null);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("cmsId", measure.getCmsId());
        report.put("title", measure.getTitle());
        report.put("reportPeriodMonths", measure.getReportPeriodMonths());
        report.put("denominator", latest != null ? latest.getDenominator() : 0L);
        report.put("exclusions", latest != null ? latest.getExclusions() : 0L);
        report.put("eligibleDenominator", latest != null ? latest.getEligibleDenominator() : 0L);
        report.put("numerator", latest != null ? latest.getNumerator() : 0L);
        report.put("performanceRate", latest != null ? latest.getPerformanceRate() : 0.0);
        report.put("performanceTarget", getTarget(cmsId));
        report.put("calculatedAt", latest != null ? latest.getCalculatedAt() : null);
        return report;
    }

    @Transactional
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

        String target = getTarget(cmsId);
        double rate = Math.round(performance * 10.0) / 10.0;

        persistResult(cmsId, denominator, exclusions, eligibleDenominator, numerator,
                rate, target, measure.getReportPeriodMonths());

        log.info("eCQM {} calculated: rate={}% denom={} num={}", cmsId, rate, denominator, numerator);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("cmsId", measure.getCmsId());
        report.put("title", measure.getTitle());
        report.put("reportPeriodMonths", measure.getReportPeriodMonths());
        report.put("denominator", denominator);
        report.put("exclusions", exclusions);
        report.put("eligibleDenominator", eligibleDenominator);
        report.put("numerator", numerator);
        report.put("performanceRate", rate);
        report.put("performanceTarget", target);
        report.put("calculatedAt", java.time.LocalDateTime.now());
        return report;
    }

    public void calculateAllMeasures() {
        List<QualityMeasure> measures = qualityMeasureRepository.findAll();
        for (QualityMeasure m : measures) {
            try {
                calculateReport(m.getCmsId());
            } catch (Exception e) {
                log.error("Failed to calculate eCQM {}", m.getCmsId(), e);
            }
        }
        log.info("Daily eCQM calculation complete: {} measures", measures.size());
    }

    public List<QualityResult> getHistory(String cmsId) {
        return qualityResultRepository.findByCmsIdOrderByCalculatedAtDesc(cmsId);
    }

    @Transactional
    protected void persistResult(String cmsId, long denominator, long exclusions,
                                  long eligibleDenominator, long numerator, double performanceRate,
                                  String target, Integer reportPeriodMonths) {
        QualityResult result = new QualityResult();
        result.setCmsId(cmsId);
        result.setDenominator(denominator);
        result.setExclusions(exclusions);
        result.setEligibleDenominator(eligibleDenominator);
        result.setNumerator(numerator);
        result.setPerformanceRate(performanceRate);
        result.setPerformanceTarget(target);
        result.setReportPeriodMonths(reportPeriodMonths);
        qualityResultRepository.save(result);
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
