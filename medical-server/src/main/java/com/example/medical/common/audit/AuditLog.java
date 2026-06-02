package com.example.medical.common.audit;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLRestriction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "audit_log")
@SQLRestriction("archived = 0")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username")
    private String username;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "module")
    private String module;

    @Column(name = "action")
    private String action;

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "detail")
    private String detail;

    @Column(name = "ip")
    private String ip;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "row_hash", length = 64)
    private String rowHash;

    @Column(name = "archived")
    private Integer archived;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) createTime = LocalDateTime.now();
        if (archived == null) archived = 0;
        this.rowHash = computeRowHash();
    }

    private String computeRowHash() {
        try {
            String data = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s",
                    userId, username, patientId, module, action,
                    targetId, detail, ip, createTime);
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
