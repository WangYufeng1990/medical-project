package com.example.medical.module.billing.dto;

import com.example.medical.module.billing.entity.PriorAuth;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PriorAuthVO {

    private Long id;
    private Long patientId;
    private String authType;
    private String itemName;
    private String itemCode;
    private String insurancePayer;
    private String status;
    private LocalDate requestedAt;
    private LocalDate resolvedAt;
    private String authNumber;
    private Long requestedBy;
    private String notes;
    private LocalDateTime createTime;

    public static PriorAuthVO fromEntity(PriorAuth pa) {
        return new PriorAuthVO(
                pa.getId(), pa.getPatientId(), pa.getAuthType(), pa.getItemName(),
                pa.getItemCode(), pa.getInsurancePayer(), pa.getStatus(),
                pa.getRequestedAt(), pa.getResolvedAt(), pa.getAuthNumber(),
                pa.getRequestedBy(), pa.getNotes(), pa.getCreateTime());
    }
}
