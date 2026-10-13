package com.example.medical.module.integration.service;

import com.example.medical.common.lookup.PatientLookup;
import com.example.medical.module.integration.dto.LabResultPayload;
import com.example.medical.module.patient.dto.LabObservationCommand;
import com.example.medical.module.patient.service.ObservationIntakeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Mirth lab-result intake. The message-level decisions are this module's — has
 * this message been seen before, does its MRN map to a patient — and the
 * observation rows themselves are the patient module's (52.5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabResultService {

    private final PatientLookup patientLookup;
    private final ObservationIntakeService observationIntake;

    @Transactional
    @com.example.medical.common.audit.Auditable(module = "integration", action = "LAB_RESULTS")
    public int processLabResults(LabResultPayload dto) {
        if (observationIntake.alreadyIngested(dto.getSourceMessageId())) {
            log.info("Duplicate lab result ignored: sourceMessageId={}", dto.getSourceMessageId());
            return 0;
        }

        Long patientId = patientLookup.idByMrn(dto.getPatientMrn()).orElse(null);
        if (patientId == null) {
            log.warn("Lab result ignored — patient MRN not found: {}", maskMrn(dto.getPatientMrn()));
            return 0;
        }

        int stored = observationIntake.ingest(patientId, toCommand(dto));
        log.info("Lab results saved: {} observations for patient mrn={}",
                stored, maskMrn(dto.getPatientMrn()));
        return stored;
    }

    private static LabObservationCommand toCommand(LabResultPayload dto) {
        List<LabObservationCommand.Item> items = dto.getResults() == null ? List.of()
                : dto.getResults().stream()
                        .map(r -> new LabObservationCommand.Item(r.getLoincCode(), r.getDisplay(),
                                r.getValue(), r.getUnit(), r.getReferenceRange(), r.getAbnormalFlag()))
                        .toList();
        return new LabObservationCommand(dto.getSourceMessageId(), dto.getCollectionDate(), items);
    }

    private static String maskMrn(String mrn) {
        if (mrn == null || mrn.length() <= 4) return "****";
        return "****" + mrn.substring(mrn.length() - 4);
    }
}
