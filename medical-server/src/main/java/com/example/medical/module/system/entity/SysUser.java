package com.example.medical.module.system.entity;

import com.example.medical.common.base.BaseEntity;
import com.example.medical.common.config.AesAttributeConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_user")
@SQLDelete(sql = "UPDATE sys_user SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class SysUser extends BaseEntity {

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "real_name")
    private String realName;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "gender")
    private Integer gender;

    @Column(name = "status")
    private Integer status;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "npi", length = 10)
    private String npi;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "state_license_number")
    private String stateLicenseNumber;

    @Column(name = "license_state", length = 2)
    private String licenseState;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "dea_number")
    private String deaNumber;

    @Column(name = "taxonomy_code", length = 10)
    private String taxonomyCode;

    @Column(name = "credentials", length = 20)
    private String credentials;

    @Column(name = "specialty", length = 100)
    private String specialty;

    @Column(name = "failed_attempts")
    private Integer failedAttempts;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
}
