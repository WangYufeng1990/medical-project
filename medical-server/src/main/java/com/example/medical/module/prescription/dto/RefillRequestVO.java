package com.example.medical.module.prescription.dto;

import com.example.medical.module.prescription.entity.RefillRequest;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefillRequestVO {

    private Long id;
    private Long patientId;
    private Long prescriptionId;
    private String status;
    private LocalDateTime requestedAt;
    private String reason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNotes;
    private LocalDateTime createTime;

    public static RefillRequestVO fromEntity(RefillRequest r) {
        return new RefillRequestVO(
                r.getId(), r.getPatientId(), r.getPrescriptionId(), r.getStatus(),
                r.getRequestedAt(), r.getReason(), r.getReviewedBy(), r.getReviewedAt(),
                r.getReviewNotes(), r.getCreateTime());
    }
}
