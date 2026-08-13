package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.Observation;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ObservationVO {

    private Long id;
    private Long patientId;
    private String loincCode;
    private String loincDisplay;
    private String obsValue;
    private String unit;
    private String referenceRange;
    private String abnormalFlag;
    private String status;
    private String sourceMessageId;
    private LocalDateTime effectiveDate;
    private LocalDateTime createTime;

    public static ObservationVO fromEntity(Observation o) {
        return new ObservationVO(o.getId(), o.getPatientId(), o.getLoincCode(), o.getLoincDisplay(),
                o.getObsValue(), o.getUnit(), o.getReferenceRange(), o.getAbnormalFlag(),
                o.getStatus(), o.getSourceMessageId(), o.getEffectiveDate(), o.getCreateTime());
    }
}
