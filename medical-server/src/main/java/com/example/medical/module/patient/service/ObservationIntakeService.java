package com.example.medical.module.patient.service;

import com.example.medical.common.audit.Auditable;
import com.example.medical.module.patient.dto.LabObservationCommand;
import com.example.medical.module.patient.entity.Observation;
import com.example.medical.module.patient.repository.ObservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns an inbound lab message into observations (52.5). The message-level
 * decisions stay with the interface that received it — whether this message was
 * seen before, and whether its MRN maps to a patient — because those are
 * questions about the feed, not about the record. What the patient module owns
 * is the row itself, and the audit row its storage produces.
 */
@Service
@RequiredArgsConstructor
public class ObservationIntakeService {

    private final ObservationRepository observationRepository;

    public boolean alreadyIngested(String sourceMessageId) {
        return observationRepository.existsBySourceMessageId(sourceMessageId);
    }

    /**
     * @return how many observations were stored
     */
    @Transactional
    @Auditable(module = "patient", action = "LAB_INGEST", phiAccess = true)
    public int ingest(Long patientId, LabObservationCommand command) {
        List<Observation> observations = new ArrayList<>();
        for (LabObservationCommand.Item item : command.results()) {
            Observation obs = new Observation();
            obs.setPatientId(patientId);
            obs.setLoincCode(item.loincCode());
            obs.setLoincDisplay(item.display());
            obs.setObsValue(item.value());
            obs.setUnit(item.unit());
            obs.setReferenceRange(item.referenceRange());
            obs.setAbnormalFlag(normalizeAbnormalFlag(item.abnormalFlag()));
            obs.setStatus("final");
            obs.setSourceMessageId(command.sourceMessageId());
            obs.setEffectiveDate(command.collectionDate());
            observations.add(obs);
        }
        observationRepository.saveAll(observations);
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
}
