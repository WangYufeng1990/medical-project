package com.example.medical.module.appointment.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * One row per doctor; used as an advisory lock target so the appointment
 * conflict check + insert run atomically per doctor (Review III H4 — prevents
 * TOCTOU double-booking).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "appointment_lock")
@SQLDelete(sql = "UPDATE appointment_lock SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class AppointmentLock extends BaseEntity {

    @Column(name = "doctor_id", unique = true, nullable = false)
    private Long doctorId;
}
