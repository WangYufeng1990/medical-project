package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.Consent;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ConsentVO {

    private Long id;
    private Long patientId;
    private String consentType;
    private String scope;
    private String status;
    private String policyUri;
    private LocalDate provisionPeriodStart;
    private LocalDate provisionPeriodEnd;
    private Long grantedBy;
    private LocalDate consentDate;
    private LocalDateTime createTime;

    public static ConsentVO fromEntity(Consent c) {
        ConsentVO vo = new ConsentVO();
        vo.setId(c.getId());
        vo.setPatientId(c.getPatientId());
        vo.setConsentType(c.getConsentType());
        vo.setScope(c.getScope());
        vo.setStatus(c.getStatus());
        vo.setPolicyUri(c.getPolicyUri());
        vo.setProvisionPeriodStart(c.getProvisionPeriodStart());
        vo.setProvisionPeriodEnd(c.getProvisionPeriodEnd());
        vo.setGrantedBy(c.getGrantedBy());
        vo.setConsentDate(c.getConsentDate());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }
}
