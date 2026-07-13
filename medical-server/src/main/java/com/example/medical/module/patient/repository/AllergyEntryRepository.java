package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.AllergyEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AllergyEntryRepository extends JpaRepository<AllergyEntry, Long>, JpaSpecificationExecutor<AllergyEntry> {

    List<AllergyEntry> findByPatientIdOrderByCreateTimeDesc(Long patientId);
}
