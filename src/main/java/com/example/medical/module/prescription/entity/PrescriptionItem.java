package com.example.medical.module.prescription.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.medical.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prescription_item")
public class PrescriptionItem extends BaseEntity {

    private Long prescriptionId;
    private String drugName;
    private String specification;
    private String dosage;
    private String frequency;
    private Integer duration;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String notes;
}
