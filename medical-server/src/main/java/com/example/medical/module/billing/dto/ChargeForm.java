package com.example.medical.module.billing.dto;

import com.example.medical.module.billing.entity.Charge;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChargeForm {

    @NotNull(message = "Patient is required")
    @Positive(message = "Patient is required")
    private Long patientId;

    private Long appointmentId;

    private Long doctorId;

    private String cptCodes;

    private String icd10Codes;

    @PositiveOrZero
    private Integer units;

    @NotNull(message = "Charge amount is required")
    @PositiveOrZero(message = "Charge amount must not be negative")
    private BigDecimal chargeAmount;

    private String visitType;

    private String notes;

    public Charge toEntity() {
        Charge c = new Charge();
        c.setPatientId(patientId);
        c.setAppointmentId(appointmentId);
        c.setDoctorId(doctorId);
        c.setCptCodes(cptCodes);
        c.setIcd10Codes(icd10Codes);
        c.setUnits(units != null ? units : 1);
        c.setChargeAmount(chargeAmount);
        c.setVisitType(visitType);
        c.setNotes(notes);
        return c;
    }
}
