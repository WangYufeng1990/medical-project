package com.example.medical.module.prescription.entity;

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
@Table(name = "cds_override")
@SQLDelete(sql = "UPDATE cds_override SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class CdsOverride extends BaseEntity {

    @Column(name = "prescription_id")
    private Long prescriptionId;

    @Column(name = "warning_type", length = 30)
    private String warningType;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "drugs_involved", length = 200)
    private String drugsInvolved;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "override_reason")
    private String overrideReason;

    @Column(name = "overridden_by")
    private Long overriddenBy;

    @Column(name = "overridden_at")
    private LocalDateTime overriddenAt;

    @PrePersist
    protected void prePersist() {
        if (overriddenAt == null) overriddenAt = LocalDateTime.now();
    }
}
