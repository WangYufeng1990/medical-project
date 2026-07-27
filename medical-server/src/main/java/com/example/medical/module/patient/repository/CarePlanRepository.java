package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.CarePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface CarePlanRepository extends JpaRepository<CarePlan, Long>, JpaSpecificationExecutor<CarePlan> {
    List<CarePlan> findByPatientIdOrderByStartDateDesc(Long patientId);
}
