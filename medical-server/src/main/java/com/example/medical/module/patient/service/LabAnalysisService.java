package com.example.medical.module.patient.service;

import com.example.medical.common.result.PageResult;
import com.example.medical.module.patient.dto.ObservationVO;
import com.example.medical.module.patient.entity.Observation;
import com.example.medical.module.patient.repository.ObservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import com.example.medical.common.base.Pages;

@Service
@RequiredArgsConstructor
public class LabAnalysisService {

    private final ObservationRepository observationRepository;

    public PageResult<ObservationVO> pageObservations(Long patientId, String loincCode, long page, long size) {
        Pageable pageable = Pages.of(page, size);
        Page<Observation> result = (loincCode == null || loincCode.isBlank())
                ? observationRepository.findByPatientIdOrderByEffectiveDateDesc(patientId, pageable)
                : observationRepository.findByPatientIdAndLoincCodeOrderByEffectiveDateDesc(patientId, loincCode, pageable);
        return PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1,
                result.getContent().stream().map(ObservationVO::fromEntity).toList());
    }

    /** Full history of a single test — bounded by definition, needed for trend rendering. */
    public List<ObservationVO> getTrend(Long patientId, String loincCode) {
        if (loincCode == null || loincCode.isBlank()) {
            return List.of();
        }
        return observationRepository
                .findByPatientIdAndLoincCodeOrderByEffectiveDateDesc(patientId, loincCode)
                .stream().map(ObservationVO::fromEntity).toList();
    }
}
