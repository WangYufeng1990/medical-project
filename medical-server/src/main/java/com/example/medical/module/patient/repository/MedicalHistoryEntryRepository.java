package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.MedicalHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface MedicalHistoryEntryRepository extends JpaRepository<MedicalHistoryEntry, Long>, JpaSpecificationExecutor<MedicalHistoryEntry> {

    List<MedicalHistoryEntry> findByPatientIdOrderByCreateTimeDesc(Long patientId);
}
