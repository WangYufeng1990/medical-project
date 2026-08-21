package com.example.medical.module.quality.service;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.patient.entity.Observation;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.ObservationRepository;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.quality.entity.QualityMeasure;
import com.example.medical.module.quality.entity.QualityResult;
import com.example.medical.module.quality.repository.QualityMeasureRepository;
import com.example.medical.module.quality.repository.QualityResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QualityMeasureService {

    private static final String HBA1C_LOINC = "4548-4";
    private static final String SYSTOLIC_LOINC = "8480-6";
    private static final String DIASTOLIC_LOINC = "8462-4";
    private static final String MAMMOGRAM_LOINC = "24606-6";

    private final QualityMeasureRepository qualityMeasureRepository;
    private final QualityResultRepository qualityResultRepository;
    private final PatientRepository patientRepository;
    private final ObservationRepository observationRepository;

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
        qualityMeasureRepository.findByCmsId(cmsId)
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

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("cmsId", cmsId);
        report.put("title", getTarget(cmsId));
        report.put("reportPeriodMonths", 12);
        report.put("denominator", denominator);
        report.put("exclusions", exclusions);
        report.put("eligibleDenominator", eligibleDenominator);
        report.put("numerator", numerator);
        report.put("performanceRate", rate);
        report.put("performanceTarget", target);
        report.put("calculatedAt", java.time.LocalDateTime.now());
        return report;
    }

    /**
     * CMS122v11 — HbA1c poor control (&gt;9%) in diabetic patients 18–75.
     * Patients without any HbA1c on record count toward the numerator.
     */
    private long[] cms122() {
        long denominator = 0, numerator = 0;
        for (Patient p : patientRepository.findAll()) {
            int age = ageInYears(p);
            if (age < 18 || age > 75) continue;
            if (!isDiabetic(p)) continue;
            denominator++;
            if (!isPoorGlycemicControl(p)) numerator++;
        }
        return new long[]{denominator, numerator, 0};
    }

    /**
     * CMS125v11 — breast cancer screening: women 50–74 with a mammogram in the
     * last 27 months. Exclusion: deceased.
     */
    private long[] cms125() {
        long denominator = 0, numerator = 0, exclusions = 0;
        LocalDate cutoff = LocalDate.now().minusMonths(27);
        for (Patient p : patientRepository.findAll()) {
            if (!"F".equals(p.getSexAtBirth())) continue;
            int age = ageInYears(p);
            if (age < 50 || age > 74) continue;
            if ("deceased".equalsIgnoreCase(p.getPatientStatus())) {
                exclusions++;
                continue;
            }
            denominator++;
            Observation mammogram = latestObservation(p.getId(), MAMMOGRAM_LOINC);
            if (mammogram != null && mammogram.getEffectiveDate() != null
                    && mammogram.getEffectiveDate().toLocalDate().isAfter(cutoff)) {
                numerator++;
            }
        }
        return new long[]{denominator, numerator, exclusions};
    }

    /**
     * CMS165v11 — controlling high blood pressure: hypertensive patients 18–85
     * whose most recent BP &lt; 140/90.
     */
    private long[] cms165() {
        long denominator = 0, numerator = 0;
        for (Patient p : patientRepository.findAll()) {
            int age = ageInYears(p);
            if (age < 18 || age > 85) continue;
            if (!isHypertensive(p)) continue;
            denominator++;
            Double systolic = latestNumeric(p.getId(), SYSTOLIC_LOINC);
            Double diastolic = latestNumeric(p.getId(), DIASTOLIC_LOINC);
            if (systolic != null && diastolic != null && systolic < 140 && diastolic < 90) {
                numerator++;
            }
        }
        return new long[]{denominator, numerator, 0};
    }

    private static boolean isDiabetic(Patient p) {
        String h = p.getMedicalHistory();
        if (h == null) return false;
        String lower = h.toLowerCase();
        return lower.contains("diabetes") || lower.contains("type 2") || lower.contains("t2dm");
    }

    private static boolean isHypertensive(Patient p) {
        String h = p.getMedicalHistory();
        return h != null && h.toLowerCase().contains("hypertension");
    }

    private boolean isPoorGlycemicControl(Patient p) {
        Double latest = latestNumeric(p.getId(), HBA1C_LOINC);
        // No HbA1c on record counts as poor control (CMS122 semantics).
        return latest == null || latest > 9.0;
    }

    private Observation latestObservation(Long patientId, String loinc) {
        List<Observation> obs = observationRepository
                .findByPatientIdAndLoincCodeOrderByEffectiveDateDesc(patientId, loinc);
        return obs.isEmpty() ? null : obs.get(0);
    }

    private Double latestNumeric(Long patientId, String loinc) {
        Observation o = latestObservation(patientId, loinc);
        if (o == null || o.getObsValue() == null) return null;
        try {
            return Double.parseDouble(o.getObsValue());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int ageInYears(Patient p) {
        if (p.getDateOfBirth() == null) return -1;
        return Period.between(p.getDateOfBirth(), LocalDate.now()).getYears();
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
