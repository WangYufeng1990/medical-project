package com.example.medical.module.prescription.repository;

import com.example.medical.module.prescription.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long>, JpaSpecificationExecutor<Prescription> {

    @Query("SELECT DISTINCT p.patientId FROM Prescription p WHERE p.doctorId = :doctorId")
    List<Long> findDistinctPatientIdsByDoctor(@Param("doctorId") Long doctorId);
}
