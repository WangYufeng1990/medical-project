package com.example.medical.module.billing.repository;

import com.example.medical.module.billing.entity.Charge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ChargeRepository extends JpaRepository<Charge, Long>, JpaSpecificationExecutor<Charge> {
    List<Charge> findByPatientIdOrderByCreateTimeDesc(Long patientId);
}
