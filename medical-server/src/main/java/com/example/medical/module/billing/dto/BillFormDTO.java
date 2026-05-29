package com.example.medical.module.billing.dto;

import com.example.medical.module.billing.entity.Bill;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BillFormDTO {

    @NotNull(message = "Patient is required")
    private Long patientId;

    private Long prescriptionId;
    private Long appointmentId;

    @NotNull(message = "Total charge is required")
    @Positive(message = "Total charge must be positive")
    private BigDecimal totalCharge;

    private String billType;
    private String cptCodes;
    private String icd10Codes;
    private String placeOfServiceCode;
    private String billingProviderNpi;
    private String renderingProviderNpi;
    private String insurancePayerName;
    private String priorAuthorizationNumber;
    private BigDecimal copayAmount;
    private LocalDate claimFilingDate;

    public Bill toEntity() {
        Bill b = new Bill();
        b.setPatientId(patientId);
        b.setPrescriptionId(prescriptionId);
        b.setAppointmentId(appointmentId);
        b.setTotalCharge(totalCharge);
        b.setBillType(billType != null ? billType : "PROFESSIONAL");
        b.setClaimStatus("DRAFT");
        b.setCptCodes(cptCodes);
        b.setIcd10Codes(icd10Codes);
        b.setPlaceOfServiceCode(placeOfServiceCode);
        b.setBillingProviderNpi(billingProviderNpi);
        b.setRenderingProviderNpi(renderingProviderNpi);
        b.setInsurancePayerName(insurancePayerName);
        b.setPriorAuthorizationNumber(priorAuthorizationNumber);
        b.setCopayAmount(copayAmount);
        b.setPatientPaidAmount(BigDecimal.ZERO);
        b.setInsuranceAdjustment(BigDecimal.ZERO);
        b.setInsurancePayment(BigDecimal.ZERO);
        b.setPatientResponsibility(totalCharge);
        b.setClaimFilingDate(claimFilingDate);
        return b;
    }
}
