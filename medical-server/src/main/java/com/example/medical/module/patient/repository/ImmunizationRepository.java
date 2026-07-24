package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.Immunization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ImmunizationRepository extends JpaRepository<Immunization, Long>, JpaSpecificationExecutor<Immunization> {
    List<Immunization> findByPatientIdOrderByAdministrationDateDesc(Long patientId);
}
