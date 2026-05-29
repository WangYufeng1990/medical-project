package com.example.medical.module.billing.entity;

import com.example.medical.common.base.BaseEntity;
import com.example.medical.common.config.AesAttributeConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "bill")
@SQLDelete(sql = "UPDATE bill SET is_deleted = 1 WHERE id = ?")
@SQLRestriction("is_deleted = 0")
public class Bill extends BaseEntity {

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "prescription_id")
    private Long prescriptionId;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "bill_type", length = 20)
    private String billType;

    @Column(name = "claim_status", length = 20)
    private String claimStatus;

    @Column(name = "total_charge")
    private BigDecimal totalCharge;

    @Column(name = "insurance_adjustment")
    private BigDecimal insuranceAdjustment;

    @Column(name = "insurance_payment")
    private BigDecimal insurancePayment;

    @Column(name = "patient_responsibility")
    private BigDecimal patientResponsibility;

    @Column(name = "patient_paid_amount")
    private BigDecimal patientPaidAmount;

    @Column(name = "copay_amount")
    private BigDecimal copayAmount;

    @Column(name = "cpt_codes", length = 200)
    private String cptCodes;

    @Column(name = "icd10_codes", length = 500)
    private String icd10Codes;

    @Column(name = "place_of_service_code", length = 5)
    private String placeOfServiceCode;

    @Column(name = "billing_provider_npi", length = 10)
    private String billingProviderNpi;

    @Column(name = "rendering_provider_npi", length = 10)
    private String renderingProviderNpi;

    @Column(name = "insurance_payer_name", length = 100)
    private String insurancePayerName;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "insurance_claim_number")
    private String insuranceClaimNumber;

    @Column(name = "prior_authorization_number", length = 50)
    private String priorAuthorizationNumber;

    @Column(name = "claim_filing_date")
    private LocalDate claimFilingDate;

    @Column(name = "adjudication_date")
    private LocalDate adjudicationDate;

    @Column(name = "pay_time")
    private LocalDateTime payTime;

    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "receipt_number", length = 50)
    private String receiptNumber;
}
