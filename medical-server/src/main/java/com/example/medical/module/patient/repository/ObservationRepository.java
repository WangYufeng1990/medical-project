package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.Observation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ObservationRepository extends JpaRepository<Observation, Long> {

    List<Observation> findByPatientIdAndLoincCodeOrderByEffectiveDateDesc(
            Long patientId, String loincCode);

    List<Observation> findByPatientIdOrderByEffectiveDateDesc(Long patientId);

    Page<Observation> findByPatientIdOrderByEffectiveDateDesc(Long patientId, Pageable pageable);

    Page<Observation> findByPatientIdAndLoincCodeOrderByEffectiveDateDesc(
            Long patientId, String loincCode, Pageable pageable);

    boolean existsBySourceMessageId(String sourceMessageId);
}
