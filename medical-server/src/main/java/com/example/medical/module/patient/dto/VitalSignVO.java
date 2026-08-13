package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.VitalSign;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class VitalSignVO {

    private Long id;
    private Long patientId;
    private Long recordedBy;
    private LocalDateTime recordedAt;
    private Integer systolicBp;
    private Integer diastolicBp;
    private Integer heartRate;
    private BigDecimal temperature;
    private Integer respiratoryRate;
    private Integer oxygenSaturation;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private BigDecimal bmi;
    private String notes;
    private LocalDateTime createTime;

    public static VitalSignVO fromEntity(VitalSign v) {
        return new VitalSignVO(v.getId(), v.getPatientId(), v.getRecordedBy(), v.getRecordedAt(),
                v.getSystolicBp(), v.getDiastolicBp(), v.getHeartRate(), v.getTemperature(),
                v.getRespiratoryRate(), v.getOxygenSaturation(), v.getHeightCm(), v.getWeightKg(),
                v.getBmi(), v.getNotes(), v.getCreateTime());
    }
}
