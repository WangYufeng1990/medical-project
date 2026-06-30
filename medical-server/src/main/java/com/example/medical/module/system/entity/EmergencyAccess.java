package com.example.medical.module.system.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "emergency_access")
public class EmergencyAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "accessed_at")
    private LocalDateTime accessedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "audited")
    private Integer audited;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (accessedAt == null) accessedAt = LocalDateTime.now();
        if (audited == null) audited = 0;
    }
}
