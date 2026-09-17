package com.example.medical.common.job;

import com.example.medical.common.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Audit-log archival. It stays in {@code common} because the table it archives is
 * {@code common}'s own — this is not a cross-module job, and the five module
 * repositories it used to inject were never read (M8.5).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionJob {

    private final AuditLogRepository auditLogRepository;

    @Value("${app.retention.audit-log-days:2190}")
    private int auditLogRetentionDays;

    @Transactional
    @Scheduled(cron = "${app.retention.cron:0 0 3 * * ?}")
    public void purgeExpiredAuditLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(auditLogRetentionDays);
        try {
            int archived = auditLogRepository.archiveByCreateTimeBefore(cutoff);
            log.info("Archived {} audit logs older than {} days (before {})", archived, auditLogRetentionDays, cutoff);
        } catch (Exception e) {
            log.error("Failed to archive audit logs", e);
        }
    }
}
