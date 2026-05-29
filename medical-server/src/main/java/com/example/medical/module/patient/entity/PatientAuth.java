package com.example.medical.module.patient.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "patient_auth")
@SQLDelete(sql = "UPDATE patient_auth SET is_deleted = 1 WHERE id = ?")
@SQLRestriction("is_deleted = 0")
public class PatientAuth extends BaseEntity {

    @Column(name = "patient_id", unique = true)
    private Long patientId;

    @Column(name = "username", unique = true)
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "status")
    private Integer status;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    @Column(name = "failed_attempts")
    private Integer failedAttempts;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
}
