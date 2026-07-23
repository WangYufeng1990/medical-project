package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long>, JpaSpecificationExecutor<Problem> {
    List<Problem> findByPatientIdOrderByOnsetDateDesc(Long patientId);
}
