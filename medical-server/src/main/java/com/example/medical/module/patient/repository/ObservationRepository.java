package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.Observation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObservationRepository extends JpaRepository<Observation, Long> {

    List<Observation> findByPatientIdAndLoincCodeOrderByEffectiveDateDesc(
            Long patientId, String loincCode);

    List<Observation> findByPatientIdOrderByEffectiveDateDesc(Long patientId);

    boolean existsBySourceMessageId(String sourceMessageId);
}
