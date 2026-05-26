package com.example.medical.module.billing.dto;

import com.example.medical.module.billing.entity.Bill;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BillVO {

    private Long id;
    private Long patientId;
    private String patientName;
    private Long prescriptionId;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private Integer status;
    private LocalDateTime payTime;
    private LocalDateTime createTime;

    public static BillVO fromEntity(Bill b, String patientName) {
        return new BillVO(b.getId(), b.getPatientId(), patientName,
                b.getPrescriptionId(), b.getAmount(), b.getPaidAmount(),
                b.getStatus(), b.getPayTime(), b.getCreateTime());
    }
}
