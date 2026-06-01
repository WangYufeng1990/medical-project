package com.example.medical.common.audit.repository;

import com.example.medical.common.audit.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>,
        JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByUserIdOrderByCreateTimeDesc(Long userId);

    List<AuditLog> findByPatientIdOrderByCreateTimeDesc(Long patientId);

    List<AuditLog> findByModuleAndActionOrderByCreateTimeDesc(String module, String action);

    List<AuditLog> findByCreateTimeBetweenOrderByCreateTimeDesc(LocalDateTime start, LocalDateTime end);

    void deleteByCreateTimeBefore(LocalDateTime cutoff);
}
