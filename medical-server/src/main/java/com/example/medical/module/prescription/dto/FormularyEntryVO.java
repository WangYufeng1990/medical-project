package com.example.medical.module.prescription.dto;

import com.example.medical.module.prescription.entity.FormularyEntry;
import lombok.Data;

@Data
public class FormularyEntryVO {

    private Long id;
    private String rxnormCode;
    private String drugName;
    private String insurancePayer;
    private String tier;
    private Boolean priorAuthRequired;
    private Boolean stepTherapyRequired;
    private String alternatives;

    public static FormularyEntryVO fromEntity(FormularyEntry e) {
        FormularyEntryVO vo = new FormularyEntryVO();
        vo.setId(e.getId());
        vo.setRxnormCode(e.getRxnormCode());
        vo.setDrugName(e.getDrugName());
        vo.setInsurancePayer(e.getInsurancePayer());
        vo.setTier(e.getTier());
        vo.setPriorAuthRequired(e.getPriorAuthRequired());
        vo.setStepTherapyRequired(e.getStepTherapyRequired());
        vo.setAlternatives(e.getAlternatives());
        return vo;
    }
}
