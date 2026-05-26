package com.example.medical.module.billing.dto;

import com.example.medical.module.billing.entity.Bill;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BillFormDTO {

    @NotNull(message = "Patient is required")
    private Long patientId;

    private Long prescriptionId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private Integer status;

    public Bill toEntity() {
        Bill b = new Bill();
        b.setPatientId(patientId);
        b.setPrescriptionId(prescriptionId);
        b.setAmount(amount);
        b.setPaidAmount(BigDecimal.ZERO);
        b.setStatus(status != null ? status : 0);
        return b;
    }
}
