package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;

public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    Page<Patient> findByIdIn(Collection<Long> ids, Pageable pageable);
}
