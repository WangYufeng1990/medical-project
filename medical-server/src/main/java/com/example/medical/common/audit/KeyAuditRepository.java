package com.example.medical.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KeyAuditRepository extends JpaRepository<KeyAudit, Long> {

    List<KeyAudit> findAllByOrderByEventTimeDesc();

    Optional<KeyAudit> findTopByEventTypeOrderByIdDesc(String eventType);
}
