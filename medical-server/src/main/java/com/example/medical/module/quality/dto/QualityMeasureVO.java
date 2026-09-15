package com.example.medical.module.quality.dto;

import com.example.medical.module.quality.entity.QualityMeasure;
import lombok.Data;

/**
 * Deliberately omits {@code denominatorQuery}, {@code numeratorQuery} and
 * {@code exclusionQuery}: the endpoints used to return the entity, so the raw SQL
 * behind each measure was part of the public API. The measure UI needs the label
 * and the reporting window; the counters come from the report endpoint.
 */
@Data
public class QualityMeasureVO {

    private Long id;
    private String cmsId;
    private String title;
    private String description;
    private Integer reportPeriodMonths;

    public static QualityMeasureVO fromEntity(QualityMeasure m) {
        QualityMeasureVO vo = new QualityMeasureVO();
        vo.setId(m.getId());
        vo.setCmsId(m.getCmsId());
        vo.setTitle(m.getTitle());
        vo.setDescription(m.getDescription());
        vo.setReportPeriodMonths(m.getReportPeriodMonths());
        return vo;
    }
}
