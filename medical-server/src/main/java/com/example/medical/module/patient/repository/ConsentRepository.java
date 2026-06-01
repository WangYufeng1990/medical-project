package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.Consent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    List<Consent> findByPatientIdOrderByCreateTimeDesc(Long patientId);

    List<Consent> findByPatientIdAndConsentTypeAndStatus(Long patientId, String consentType, String status);
}
