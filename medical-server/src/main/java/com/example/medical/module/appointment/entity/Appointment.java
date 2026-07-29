package com.example.medical.module.appointment.entity;

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
@Table(name = "appointment")
@SQLDelete(sql = "UPDATE appointment SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class Appointment extends BaseEntity {

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "doctor_id")
    private Long doctorId;

    @Column(name = "appointment_time")
    private LocalDateTime appointmentTime;

    /**
     * 0=scheduled, 1=arrived, 2=cancelled, 3=completed,
     * 4=no-show, 5=rescheduled, 6=in-progress
     */
    @Column(name = "status")
    private Integer status;

    @Column(name = "visit_type", length = 30)
    private String visitType;

    @Column(name = "chief_complaint")
    private String chiefComplaint;

    @Column(name = "department", length = 50)
    private String department;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "cpt_code", length = 10)
    private String cptCode;

    @Column(name = "icd10_codes", length = 200)
    private String icd10Codes;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "description")
    private String description;

    @Column(name = "notes")
    private String notes;
}
