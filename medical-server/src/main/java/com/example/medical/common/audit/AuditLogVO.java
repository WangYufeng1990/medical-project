package com.example.medical.common.audit;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditLogVO {

    private Long id;
    private Long userId;
    private String username;
    private Long patientId;
    private String module;
    private String action;
    private String targetId;
    private String detail;
    private String ip;
    private LocalDateTime createTime;
    private String rowHash;
    private String prevHash;

    public static AuditLogVO fromEntity(AuditLog log) {
        return new AuditLogVO(
                log.getId(),
                log.getUserId(),
                log.getUsername(),
                log.getPatientId(),
                log.getModule(),
                log.getAction(),
                log.getTargetId(),
                log.getDetail(),
                log.getIp(),
                log.getCreateTime(),
                log.getRowHash(),
                log.getPrevHash());
    }
}
