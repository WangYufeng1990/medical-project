package com.example.medical.module.patient.service;

import com.example.medical.common.lookup.ClinicalMeasureReader;
import com.example.medical.module.patient.entity.Observation;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.ObservationRepository;
import com.example.medical.module.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PatientMeasureReader implements ClinicalMeasureReader {

    private final PatientRepository patientRepository;
    private final ObservationRepository observationRepository;

    @Override
    public Page<MeasurePatientRow> patientPage(Pageable pageable) {
        return patientRepository.findAll(pageable).map(PatientMeasureReader::toRow);
    }

    /**
     * One query for the whole page. The ordering is the one the measures already
     * relied on — most recent first — so the first row seen for a patient/LOINC is
     * the row they would have picked one query at a time.
     */
    @Override
    public Map<Long, Map<String, LatestObservation>> latestObservations(Collection<Long> patientIds,
                                                                       Collection<String> loincs) {
        if (patientIds.isEmpty() || loincs.isEmpty()) return Map.of();

        Map<Long, Map<String, LatestObservation>> latest = new HashMap<>();
        List<Observation> rows = observationRepository
                .findByPatientIdInAndLoincCodeInOrderByEffectiveDateDesc(patientIds, loincs);
        for (Observation o : rows) {
            latest.computeIfAbsent(o.getPatientId(), id -> new HashMap<>())
                    .putIfAbsent(o.getLoincCode(),
                            new LatestObservation(o.getObsValue(), o.getEffectiveDate()));
        }
        return latest;
    }

    private static MeasurePatientRow toRow(Patient p) {
        return new MeasurePatientRow(p.getId(), ageInYears(p.getDateOfBirth()), p.getSexAtBirth(),
                p.getPatientStatus(), p.getMedicalHistory());
    }

    /** -1 when there is no date of birth, which every measure reads as out of range. */
    private static int ageInYears(LocalDate dateOfBirth) {
        return Optional.ofNullable(dateOfBirth)
                .map(dob -> Period.between(dob, LocalDate.now()).getYears())
                .orElse(-1);
    }
}
