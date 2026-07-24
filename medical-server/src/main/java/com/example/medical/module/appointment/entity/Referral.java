package com.example.medical.module.appointment.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "referral")
@EqualsAndHashCode(callSuper = true)
@SQLDelete(sql = "UPDATE referral SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class Referral extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "referring_doctor_id", nullable = false)
    private Long referringDoctorId;

    @Column(name = "specialist_name", length = 200, nullable = false)
    private String specialistName;

    @Column(name = "specialist_npi", length = 10)
    private String specialistNpi;

    @Column(length = 100)
    private String specialty;

    @Column(length = 200)
    private String diagnosis;

    @Column(length = 500)
    private String reason;

    @Column(length = 20)
    private String urgency = "ROUTINE";

    @Column(length = 20, nullable = false)
    private String status = "PENDING";

    @Column(name = "referral_date")
    private LocalDate referralDate;

    @Column(name = "appointment_date")
    private LocalDate appointmentDate;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Column(length = 500)
    private String notes;
}
