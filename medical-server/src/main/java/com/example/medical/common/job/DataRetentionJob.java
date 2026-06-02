package com.example.medical.common.job;

import com.example.medical.common.audit.repository.AuditLogRepository;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import com.example.medical.module.billing.repository.BillRepository;
import com.example.medical.module.chat.repository.MessageRepository;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionJob {

    private final AuditLogRepository auditLogRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final BillRepository billRepository;
    private final MessageRepository messageRepository;

    @Value("${app.retention.audit-log-days:2190}")
    private int auditLogRetentionDays;

    @Value("${app.retention.soft-delete-days:365}")
    private int softDeleteRetentionDays;

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
