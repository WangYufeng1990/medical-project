package com.example.medical.common.audit;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "key_audit")
public class KeyAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", length = 20)
    private String eventType;

    @Column(name = "key_version", length = 10)
    private String keyVersion;

    @Column(name = "changed_by", length = 50)
    private String changedBy;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "event_time")
    private LocalDateTime eventTime;

    @PrePersist
    protected void onCreate() {
        if (eventTime == null) eventTime = LocalDateTime.now();
    }
}
