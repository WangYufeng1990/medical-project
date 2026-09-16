package com.example.medical.module.system.dto;

import com.example.medical.module.system.entity.EmergencyAccess;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EmergencyAccessVO {

    private Long id;
    private Long userId;
    private Long patientId;
    private String reason;
    private LocalDateTime accessedAt;
    private LocalDateTime expiresAt;
    private Integer audited;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;

    public static EmergencyAccessVO fromEntity(EmergencyAccess e) {
        return new EmergencyAccessVO(
                e.getId(), e.getUserId(), e.getPatientId(), e.getReason(),
                e.getAccessedAt(), e.getExpiresAt(), e.getAudited(),
                e.getReviewedBy(), e.getReviewedAt());
    }
}
