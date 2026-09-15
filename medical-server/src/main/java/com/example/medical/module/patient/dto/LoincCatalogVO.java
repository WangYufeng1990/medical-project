package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.LoincCatalog;
import lombok.Data;

@Data
public class LoincCatalogVO {

    private Long id;
    private String loincCode;
    private String display;
    private String unit;
    private String refRangeLow;
    private String refRangeHigh;
    private String panelParentCode;

    public static LoincCatalogVO fromEntity(LoincCatalog c) {
        LoincCatalogVO vo = new LoincCatalogVO();
        vo.setId(c.getId());
        vo.setLoincCode(c.getLoincCode());
        vo.setDisplay(c.getDisplay());
        vo.setUnit(c.getUnit());
        vo.setRefRangeLow(c.getRefRangeLow());
        vo.setRefRangeHigh(c.getRefRangeHigh());
        vo.setPanelParentCode(c.getPanelParentCode());
        return vo;
    }
}
