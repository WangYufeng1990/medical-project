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
@Table(name = "immunization")
@EqualsAndHashCode(callSuper = true)
@SQLDelete(sql = "UPDATE immunization SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class Immunization extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "vaccine_name", length = 200, nullable = false)
    private String vaccineName;

    @Column(name = "cvx_code", length = 10)
    private String cvxCode;

    @Column(name = "administration_date")
    private LocalDate administrationDate;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(length = 100)
    private String manufacturer;

    @Column(name = "dose_number", length = 20)
    private String doseNumber;

    @Column(length = 50)
    private String site;

    @Column(length = 50)
    private String route;

    @Column(length = 20, nullable = false)
    private String status = "completed";

    @Column(name = "administered_by")
    private Long administeredBy;

    @Column(length = 500)
    private String notes;
}
