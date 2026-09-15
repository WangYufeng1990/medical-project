package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.AllergyEntry;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AllergyEntryVO {

    private Long id;
    private Long patientId;
    private String allergen;
    private String reaction;
    private String severity;
    private Long recordedBy;
    private String status;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createTime;

    public static AllergyEntryVO fromEntity(AllergyEntry e) {
        AllergyEntryVO vo = new AllergyEntryVO();
        vo.setId(e.getId());
        vo.setPatientId(e.getPatientId());
        vo.setAllergen(e.getAllergen());
        vo.setReaction(e.getReaction());
        vo.setSeverity(e.getSeverity());
        vo.setRecordedBy(e.getRecordedBy());
        vo.setStatus(e.getStatus());
        vo.setResolvedBy(e.getResolvedBy());
        vo.setResolvedAt(e.getResolvedAt());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
