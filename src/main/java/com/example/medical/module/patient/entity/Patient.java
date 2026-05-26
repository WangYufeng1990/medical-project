package com.example.medical.module.patient.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.medical.common.base.BaseEntity;
import com.example.medical.common.config.AesTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("patient")
public class Patient extends BaseEntity {

    private String username;
    private String password;
    private String name;
    private Integer gender;
    private Integer age;

    @TableField(typeHandler = AesTypeHandler.class)
    private String idCard;

    @TableField(typeHandler = AesTypeHandler.class)
    private String phone;

    private String address;
    private String medicalHistory;
    private String allergies;
}
