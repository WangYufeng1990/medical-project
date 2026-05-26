package com.example.medical.module.billing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.medical.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bill")
public class Bill extends BaseEntity {

    private Long patientId;
    private Long prescriptionId;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private Integer status;
    private LocalDateTime payTime;
}
