package com.example.medical.module.prescription.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cds_override")
public class CdsOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prescription_id")
    private Long prescriptionId;

    @Column(name = "warning_type", length = 30)
    private String warningType;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "drugs_involved", length = 200)
    private String drugsInvolved;

    @Column(name = "override_reason", length = 500)
    private String overrideReason;

    @Column(name = "overridden_by")
    private Long overriddenBy;

    @Column(name = "overridden_at")
    private LocalDateTime overriddenAt;

    @PrePersist
    protected void onCreate() {
        if (overriddenAt == null) overriddenAt = LocalDateTime.now();
    }
}
