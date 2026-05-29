package com.example.medical.module.billing.dto;

import com.example.medical.module.billing.entity.Bill;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BillVO {

    private Long id;
    private Long patientId;
    private String patientName;
    private Long prescriptionId;
    private Long appointmentId;
    private String billType;
    private String claimStatus;
    private BigDecimal totalCharge;
    private BigDecimal insuranceAdjustment;
    private BigDecimal insurancePayment;
    private BigDecimal patientResponsibility;
    private BigDecimal patientPaidAmount;
    private BigDecimal copayAmount;
    private BigDecimal balanceDue;
    private String cptCodes;
    private String icd10Codes;
    private String placeOfServiceCode;
    private String insurancePayerName;
    private String insuranceClaimNumberLast4;
    private String priorAuthorizationNumber;
    private LocalDate claimFilingDate;
    private LocalDate adjudicationDate;
    private LocalDateTime payTime;
    private String paymentMethod;
    private LocalDateTime createTime;

    public static BillVO fromEntity(Bill b, String patientName) {
        BigDecimal balance = b.getPatientResponsibility() != null && b.getPatientPaidAmount() != null
                ? b.getPatientResponsibility().subtract(b.getPatientPaidAmount())
                : BigDecimal.ZERO;
        return new BillVO(
                b.getId(), b.getPatientId(), patientName,
                b.getPrescriptionId(), b.getAppointmentId(),
                b.getBillType(), b.getClaimStatus(),
                b.getTotalCharge(), b.getInsuranceAdjustment(),
                b.getInsurancePayment(), b.getPatientResponsibility(),
                b.getPatientPaidAmount(), b.getCopayAmount(),
                balance,
                b.getCptCodes(), b.getIcd10Codes(),
                b.getPlaceOfServiceCode(),
                b.getInsurancePayerName(),
                maskLast4(b.getInsuranceClaimNumber()),
                b.getPriorAuthorizationNumber(),
                b.getClaimFilingDate(), b.getAdjudicationDate(),
                b.getPayTime(), b.getPaymentMethod(),
                b.getCreateTime());
    }

    private static String maskLast4(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() <= 4) return "****";
        return "****" + value.substring(value.length() - 4);
    }
}
