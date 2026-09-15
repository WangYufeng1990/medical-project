package com.example.medical.common.audit;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KeyAuditVO {

    private Long id;
    private String eventType;
    private String keyVersion;
    private String changedBy;
    private String detail;
    private LocalDateTime eventTime;

    public static KeyAuditVO fromEntity(KeyAudit a) {
        KeyAuditVO vo = new KeyAuditVO();
        vo.setId(a.getId());
        vo.setEventType(a.getEventType());
        vo.setKeyVersion(a.getKeyVersion());
        vo.setChangedBy(a.getChangedBy());
        vo.setDetail(a.getDetail());
        vo.setEventTime(a.getEventTime());
        return vo;
    }
}
