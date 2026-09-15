package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.MedicalHistoryEntry;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MedicalHistoryEntryVO {

    private Long id;
    private Long patientId;
    private String description;
    private Long recordedBy;
    private LocalDateTime createTime;

    public static MedicalHistoryEntryVO fromEntity(MedicalHistoryEntry e) {
        MedicalHistoryEntryVO vo = new MedicalHistoryEntryVO();
        vo.setId(e.getId());
        vo.setPatientId(e.getPatientId());
        vo.setDescription(e.getDescription());
        vo.setRecordedBy(e.getRecordedBy());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
