package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.PatientAuth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientAuthRepository extends JpaRepository<PatientAuth, Long> {

    Optional<PatientAuth> findByUsername(String username);

    Optional<PatientAuth> findByPatientId(Long patientId);
}
