package com.example.medical.module.patient.service;

import com.example.medical.module.patient.entity.LoincCatalog;
import com.example.medical.module.patient.entity.Observation;
import com.example.medical.module.patient.repository.LoincCatalogRepository;
import com.example.medical.module.patient.repository.ObservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LabAnalysisService {

    private final ObservationRepository observationRepository;
    private final LoincCatalogRepository loincCatalogRepository;

    public List<Observation> getTrend(Long patientId, String loincCode) {
        if (loincCode == null || loincCode.isBlank()) {
            return observationRepository.findByPatientIdOrderByEffectiveDateDesc(patientId);
        }
        return observationRepository
                .findByPatientIdAndLoincCodeOrderByEffectiveDateDesc(patientId, loincCode);
    }

    public String autoFlag(String loincCode, String value) {
        if (value == null || value.isBlank()) return null;
        Optional<LoincCatalog> catalog = loincCatalogRepository.findByLoincCode(loincCode);
        if (catalog.isEmpty()) return null;

        LoincCatalog c = catalog.get();
        if (c.getRefRangeLow() == null || c.getRefRangeHigh() == null) return null;

        try {
            double val = Double.parseDouble(value);
            double low = Double.parseDouble(c.getRefRangeLow());
            double high = Double.parseDouble(c.getRefRangeHigh());

            if (val < low * 0.8) return "LL";
            if (val < low) return "L";
            if (val > high * 1.5) return "HH";
            if (val > high) return "H";
            return "N";
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
