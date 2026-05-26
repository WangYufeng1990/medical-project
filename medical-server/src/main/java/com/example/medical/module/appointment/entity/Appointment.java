package com.example.medical.module.appointment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.medical.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appointment")
public class Appointment extends BaseEntity {

    private Long patientId;
    private Long doctorId;
    private LocalDateTime appointmentTime;
    private Integer status;
    private String description;
    private String notes;
}
