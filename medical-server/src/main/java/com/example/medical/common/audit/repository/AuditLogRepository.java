package com.example.medical.common.audit.repository;

import com.example.medical.common.audit.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>,
        JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByUserIdOrderByCreateTimeDesc(Long userId);

    List<AuditLog> findByPatientIdOrderByCreateTimeDesc(Long patientId);

    List<AuditLog> findByModuleAndActionOrderByCreateTimeDesc(String module, String action);

    List<AuditLog> findByCreateTimeBetweenOrderByCreateTimeDesc(LocalDateTime start, LocalDateTime end);

    @Modifying
    @Query("UPDATE AuditLog a SET a.archived = 1 WHERE a.createTime < :cutoff AND a.archived = 0")
    int archiveByCreateTimeBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT DISTINCT a.module, a.action FROM AuditLog a WHERE a.module IS NOT NULL AND a.action IS NOT NULL")
    List<Object[]> findDistinctModulesAndActions();

    List<AuditLog> findAllByOrderByIdAsc();

    java.util.Optional<AuditLog> findTopByOrderByIdDesc();
}
