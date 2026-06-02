package com.example.medical.module.integration.service;

import com.example.medical.module.integration.dto.LabResultDTO;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class LabResultService {

    private final PatientRepository patientRepository;
    private final ObservationRepository observationRepository;

    @Transactional
    public int processLabResults(LabResultDTO dto) {
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
        for (LabResultDTO.ResultItem item : dto.getResults()) {
            Observation obs = new Observation();
            obs.setPatientId(patient.getId());
            obs.setLoincCode(item.getLoincCode());
            obs.setLoincDisplay(item.getDisplay());
            obs.setObsValue(item.getValue());
            obs.setUnit(item.getUnit());
            obs.setReferenceRange(item.getReferenceRange());
            obs.setAbnormalFlag(item.getAbnormalFlag());
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

    private static String maskMrn(String mrn) {
        if (mrn == null || mrn.length() <= 4) return "****";
        return "****" + mrn.substring(mrn.length() - 4);
    }
}
