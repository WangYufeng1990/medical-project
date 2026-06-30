package com.example.medical.module.system.repository;

import com.example.medical.module.system.entity.EmergencyAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface EmergencyAccessRepository extends JpaRepository<EmergencyAccess, Long>,
        JpaSpecificationExecutor<EmergencyAccess> {

    List<EmergencyAccess> findByUserIdOrderByAccessedAtDesc(Long userId);

    List<EmergencyAccess> findByPatientIdOrderByAccessedAtDesc(Long patientId);

    List<EmergencyAccess> findByAuditedOrderByAccessedAtDesc(Integer audited);
}
