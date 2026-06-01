package com.example.medical.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KeyAuditRepository extends JpaRepository<KeyAudit, Long> {

    List<KeyAudit> findAllByOrderByEventTimeDesc();
}
