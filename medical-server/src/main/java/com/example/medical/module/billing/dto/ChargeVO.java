package com.example.medical.module.billing.dto;

import com.example.medical.module.billing.entity.Charge;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChargeVO {

    private Long id;
    private Long patientId;
    private Long appointmentId;
    private Long doctorId;
    private String cptCodes;
    private String icd10Codes;
    private Integer units;
    private BigDecimal chargeAmount;
    private String visitType;
    private String status;
    private String notes;
    private Long billId;
    private LocalDateTime createTime;

    public static ChargeVO fromEntity(Charge c) {
        return new ChargeVO(c.getId(), c.getPatientId(), c.getAppointmentId(), c.getDoctorId(),
                c.getCptCodes(), c.getIcd10Codes(), c.getUnits(), c.getChargeAmount(),
                c.getVisitType(), c.getStatus(), c.getNotes(), c.getBillId(), c.getCreateTime());
    }
}
