package com.example.medical.module.quality.service;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.lookup.ClinicalMeasureReader;
import com.example.medical.common.lookup.ClinicalMeasureReader.LatestObservation;
import com.example.medical.common.lookup.ClinicalMeasureReader.MeasurePatientRow;
import com.example.medical.module.quality.dto.QualityReportVO;
import com.example.medical.module.quality.entity.QualityMeasure;
import com.example.medical.module.quality.entity.QualityResult;
import com.example.medical.module.quality.repository.QualityMeasureRepository;
import com.example.medical.module.quality.repository.QualityResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityMeasureService {

    private static final String HBA1C_LOINC = "4548-4";
    private static final String SYSTOLIC_LOINC = "8480-6";
    private static final String DIASTOLIC_LOINC = "8462-4";
    private static final String MAMMOGRAM_LOINC = "24606-6";

    private static final int PATIENT_PAGE_SIZE = 500;
    private static final Set<String> HBA1C_LOINCS = Set.of(HBA1C_LOINC);
    private static final Set<String> BP_LOINCS = Set.of(SYSTOLIC_LOINC, DIASTOLIC_LOINC);
    private static final Set<String> MAMMOGRAM_LOINCS = Set.of(MAMMOGRAM_LOINC);

    private final QualityMeasureRepository qualityMeasureRepository;
    private final QualityResultRepository qualityResultRepository;
    private final ClinicalMeasureReader measureReader;

    public List<QualityMeasure> listMeasures() {
        return qualityMeasureRepository.findAll();
    }

    public QualityReportVO getReport(String cmsId) {
        QualityMeasure measure = qualityMeasureRepository.findByCmsId(cmsId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND,
                        "Measure not found: " + cmsId));

        QualityResult latest = qualityResultRepository
                .findTopByCmsIdOrderByCalculatedAtDesc(cmsId).orElse(null);

        return QualityReportVO.of(
                measure.getCmsId(),
                measure.getTitle(),
                measure.getReportPeriodMonths(),
                latest != null ? latest.getDenominator() : 0L,
                latest != null ? latest.getExclusions() : 0L,
                latest != null ? latest.getEligibleDenominator() : 0L,
                latest != null ? latest.getNumerator() : 0L,
                latest != null ? latest.getPerformanceRate() : 0.0,
                getTarget(cmsId),
                latest != null ? latest.getCalculatedAt() : null);
    }

    @Transactional
    public QualityReportVO calculateReport(String cmsId) {
        QualityMeasure measure = qualityMeasureRepository.findByCmsId(cmsId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND,
                        "Measure not found: " + cmsId));

        // Review III H6: the old implementation executed raw SQL against
        // AES-encrypted columns (medical_history, date_of_birth) and silently
        // computed 0. Measures are now evaluated in memory over decrypted
        // entities so the LIKE/date logic is meaningful again.
        long[] counts = switch (cmsId) {
            case "CMS122v11" -> cms122();
            case "CMS125v11" -> cms125();
            case "CMS165v11" -> cms165();
            default -> throw new BusinessException(ResultCode.NOT_FOUND,
                    "No calculation implemented for measure: " + cmsId);
        };
        long denominator = counts[0];
        long numerator = counts[1];
        long exclusions = counts[2];

        long eligibleDenominator = denominator - exclusions;
        double performance = eligibleDenominator > 0
                ? (double) numerator / eligibleDenominator * 100.0 : 0.0;

        String target = getTarget(cmsId);
        double rate = Math.round(performance * 10.0) / 10.0;

        persistResult(cmsId, denominator, exclusions, eligibleDenominator, numerator,
                rate, target, 12);

        log.info("eCQM {} calculated: rate={}% denom={} num={}", cmsId, rate, denominator, numerator);

        // title is the measure's own name here too — it used to be the target
        // description, so the same key meant two different things depending on
        // which of the two report endpoints you called (Round 51.8).
        return QualityReportVO.of(cmsId, measure.getTitle(), 12, denominator, exclusions,
                eligibleDenominator, numerator, rate, target,
                java.time.LocalDateTime.now());
    }

    /**
     * CMS122v11 — HbA1c poor control (&gt;9%) in diabetic patients 18–75.
     * Patients without any HbA1c on record count toward the numerator.
     */
    private long[] cms122() {
        long[] counts = new long[3];
        walkPatients(HBA1C_LOINCS, (patient, latest) -> {
            if (patient.ageYears() < 18 || patient.ageYears() > 75) return;
            if (!isDiabetic(patient)) return;
            counts[0]++;
            if (!isPoorGlycemicControl(latest.get(HBA1C_LOINC))) counts[1]++;
        });
        return counts;
    }

    /**
     * CMS125v11 — breast cancer screening: women 50–74 with a mammogram in the
     * last 27 months. Exclusion: deceased.
     */
    private long[] cms125() {
        long[] counts = new long[3];
        LocalDate cutoff = LocalDate.now().minusMonths(27);
        walkPatients(MAMMOGRAM_LOINCS, (patient, latest) -> {
            if (!"F".equals(patient.sexAtBirth())) return;
            if (patient.ageYears() < 50 || patient.ageYears() > 74) return;
            if ("deceased".equalsIgnoreCase(patient.patientStatus())) {
                counts[2]++;
                return;
            }
            counts[0]++;
            LatestObservation mammogram = latest.get(MAMMOGRAM_LOINC);
            if (mammogram != null && mammogram.effectiveDate() != null
                    && mammogram.effectiveDate().toLocalDate().isAfter(cutoff)) {
                counts[1]++;
            }
        });
        return counts;
    }

    /**
     * CMS165v11 — controlling high blood pressure: hypertensive patients 18–85
     * whose most recent BP &lt; 140/90.
     */
    private long[] cms165() {
        long[] counts = new long[3];
        walkPatients(BP_LOINCS, (patient, latest) -> {
            if (patient.ageYears() < 18 || patient.ageYears() > 85) return;
            if (!isHypertensive(patient)) return;
            counts[0]++;
            Double systolic = numeric(latest.get(SYSTOLIC_LOINC));
            Double diastolic = numeric(latest.get(DIASTOLIC_LOINC));
            if (systolic != null && diastolic != null && systolic < 140 && diastolic < 90) {
                counts[1]++;
            }
        });
        return counts;
    }

    /**
     * Every patient, one page at a time, with the latest value of the LOINCs the
     * measure reads — one observation query per page instead of one per patient.
     */
    private void walkPatients(Set<String> loincs, PatientMeasure measure) {
        for (int page = 0; ; page++) {
            Page<MeasurePatientRow> patients =
                    measureReader.patientPage(PageRequest.of(page, PATIENT_PAGE_SIZE));
            Map<Long, Map<String, LatestObservation>> latest = measureReader.latestObservations(
                    patients.map(MeasurePatientRow::patientId).toList(), loincs);
            for (MeasurePatientRow patient : patients) {
                measure.accept(patient, latest.getOrDefault(patient.patientId(), Map.of()));
            }
            if (!patients.hasNext()) return;
        }
    }

    private static boolean isDiabetic(MeasurePatientRow patient) {
        String history = patient.conditionText();
        if (history == null) return false;
        String lower = history.toLowerCase();
        return lower.contains("diabetes") || lower.contains("type 2") || lower.contains("t2dm");
    }

    private static boolean isHypertensive(MeasurePatientRow patient) {
        String history = patient.conditionText();
        return history != null && history.toLowerCase().contains("hypertension");
    }

    private static boolean isPoorGlycemicControl(LatestObservation hba1c) {
        Double latest = numeric(hba1c);
        // No HbA1c on record counts as poor control (CMS122 semantics).
        return latest == null || latest > 9.0;
    }

    private static Double numeric(LatestObservation observation) {
        if (observation == null || observation.value() == null) return null;
        try {
            return Double.parseDouble(observation.value());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface PatientMeasure {
        void accept(MeasurePatientRow patient, Map<String, LatestObservation> latest);
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

    private String getTarget(String cmsId) {
        return switch (cmsId) {
            case "CMS122v11" -> "HbA1c < 9% in diabetic patients. Target: ≥ 70%";
            case "CMS125v11" -> "Breast cancer screening in women 50-74. Target: ≥ 70%";
            case "CMS165v11" -> "Blood pressure < 140/90 in hypertensive patients. Target: ≥ 60%";
            default -> "Target not defined";
        };
    }
}
