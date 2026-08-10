package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.Problem;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProblemVO {

    private Long id;
    private Long patientId;
    private String snomedCode;
    private String snomedDisplay;
    private String icd10Code;
    private LocalDate onsetDate;
    private LocalDate resolutionDate;
    private String status;
    private String severity;
    private Long recordedBy;
    private String notes;
    private LocalDateTime createTime;

    public static ProblemVO fromEntity(Problem p) {
        return new ProblemVO(
                p.getId(), p.getPatientId(), p.getSnomedCode(), p.getSnomedDisplay(),
                p.getIcd10Code(), p.getOnsetDate(), p.getResolutionDate(), p.getStatus(),
                p.getSeverity(), p.getRecordedBy(), p.getNotes(), p.getCreateTime());
    }
}
