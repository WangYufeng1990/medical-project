package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.VitalSign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface VitalSignRepository extends JpaRepository<VitalSign, Long>, JpaSpecificationExecutor<VitalSign> {
    List<VitalSign> findByPatientIdOrderByRecordedAtDesc(Long patientId);
}
