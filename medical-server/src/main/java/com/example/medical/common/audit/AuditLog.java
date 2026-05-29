package com.example.medical.common.audit;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "audit_log")
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

    @PrePersist
    protected void onCreate() {
        if (createTime == null) createTime = LocalDateTime.now();
    }
}
