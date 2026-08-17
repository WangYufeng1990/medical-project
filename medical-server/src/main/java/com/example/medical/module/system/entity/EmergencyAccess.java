package com.example.medical.module.system.entity;

import com.example.medical.common.base.BaseEntity;
import com.example.medical.common.config.AesAttributeConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "emergency_access")
@SQLDelete(sql = "UPDATE emergency_access SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class EmergencyAccess extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "patient_id")
    private Long patientId;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "reason")
    private String reason;

    @Column(name = "accessed_at")
    private LocalDateTime accessedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "audited")
    private Integer audited = 0;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void prePersist() {
        if (accessedAt == null) accessedAt = LocalDateTime.now();
    }
}
