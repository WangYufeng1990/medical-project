package com.example.medical.module.integration.service;

import com.example.medical.module.integration.dto.LabResultPayload;
import com.example.medical.module.patient.entity.Observation;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.ObservationRepository;
import com.example.medical.module.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class LabResultService {

    private final PatientRepository patientRepository;
    private final ObservationRepository observationRepository;

    @Transactional
    @com.example.medical.common.audit.Auditable(module = "integration", action = "LAB_RESULTS")
    public int processLabResults(LabResultPayload dto) {
        if (observationRepository.existsBySourceMessageId(dto.getSourceMessageId())) {
            log.info("Duplicate lab result ignored: sourceMessageId={}", dto.getSourceMessageId());
            return 0;
        }

        Patient patient = patientRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("mrn"), dto.getPatientMrn()),
                org.springframework.data.domain.Sort.unsorted())
                .stream().findFirst()
                .orElse(null);

        if (patient == null) {
            log.warn("Lab result ignored — patient MRN not found: {}", maskMrn(dto.getPatientMrn()));
            return 0;
        }

        List<Observation> observations = new ArrayList<>();
        for (LabResultPayload.ResultItem item : dto.getResults()) {
            Observation obs = new Observation();
            obs.setPatientId(patient.getId());
            obs.setLoincCode(item.getLoincCode());
            obs.setLoincDisplay(item.getDisplay());
            obs.setObsValue(item.getValue());
            obs.setUnit(item.getUnit());
            obs.setReferenceRange(item.getReferenceRange());
            obs.setAbnormalFlag(normalizeAbnormalFlag(item.getAbnormalFlag()));
            obs.setStatus("final");
            obs.setSourceMessageId(dto.getSourceMessageId());
            obs.setEffectiveDate(dto.getCollectionDate());
            observations.add(obs);
        }

        observationRepository.saveAll(observations);
        log.info("Lab results saved: {} observations for patient mrn={}",
                observations.size(), maskMrn(dto.getPatientMrn()));
        return observations.size();
    }

    /**
     * The column is {@code abnormal_flag CHAR(1)}, but HL7 (and every lab feed
     * worth the name) also sends the two-character critical values, which used to
     * fail the whole message with a 500 "Value too long for column". Normalised
     * here, before storage — once an {@code HH} is in a one-character column the
     * distinction is gone for good. An unrecognised flag becomes null rather than
     * a guess.
     */
    static String normalizeAbnormalFlag(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "H", "HH", "HU" -> "H";
            case "L", "LL", "LU" -> "L";
            case "N" -> "N";
            case "A" -> "A";
            default -> null;
        };
    }

    private static String maskMrn(String mrn) {
        if (mrn == null || mrn.length() <= 4) return "****";
        return "****" + mrn.substring(mrn.length() - 4);
    }
}
