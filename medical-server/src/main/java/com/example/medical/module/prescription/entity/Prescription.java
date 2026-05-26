package com.example.medical.module.prescription.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.medical.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prescription")
public class Prescription extends BaseEntity {

    private Long patientId;
    private Long doctorId;
    private String diagnosis;
    private LocalDate prescriptionDate;
}
