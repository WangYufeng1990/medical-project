package com.example.medical.module.billing.repository;

import com.example.medical.module.billing.entity.PriorAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PriorAuthRepository extends JpaRepository<PriorAuth, Long>, JpaSpecificationExecutor<PriorAuth> {
    List<PriorAuth> findByPatientIdOrderByRequestedAtDesc(Long patientId);
}
