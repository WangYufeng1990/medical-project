package com.example.medical.module.patient.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "care_plan")
@EqualsAndHashCode(callSuper = true)
@SQLDelete(sql = "UPDATE care_plan SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class CarePlan extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(length = 200)
    private String goal;

    @Column(name = "interventions", length = 1000)
    private String interventions;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(length = 20, nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(length = 500)
    private String notes;
}
