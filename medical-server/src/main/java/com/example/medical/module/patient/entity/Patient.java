package com.example.medical.module.patient.entity;

import com.example.medical.common.base.BaseEntity;
import com.example.medical.common.config.AesAttributeConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "patient")
@SQLDelete(sql = "UPDATE patient SET is_deleted = 1 WHERE id = ?")
@SQLRestriction("is_deleted = 0")
public class Patient extends BaseEntity {

    @Column(name = "mrn", unique = true)
    private String mrn;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "ssn")
    private String ssn;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "name")
    private String name;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "sex_at_birth", length = 1)
    private String sexAtBirth;

    @Column(name = "gender_identity", length = 50)
    private String genderIdentity;

    @Column(name = "race", length = 100)
    private String race;

    @Column(name = "ethnicity", length = 50)
    private String ethnicity;

    @Column(name = "preferred_language", length = 10)
    private String preferredLanguage;

    @Column(name = "marital_status", length = 20)
    private String maritalStatus;

    @Column(name = "patient_status", length = 20)
    private String patientStatus;

    @Column(name = "primary_care_provider", length = 100)
    private String primaryCareProvider;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "phone_mobile")
    private String phoneMobile;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "phone_home")
    private String phoneHome;

    @Column(name = "phone_work", length = 20)
    private String phoneWork;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "address_line1", length = 100)
    private String addressLine1;

    @Column(name = "address_line2", length = 100)
    private String addressLine2;

    @Column(name = "city", length = 50)
    private String city;

    @Column(name = "state", length = 2)
    private String state;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_relation", length = 50)
    private String emergencyContactRelation;

    @Column(name = "insurance_payer", length = 100)
    private String insurancePayer;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "insurance_member_id")
    private String insuranceMemberId;

    @Column(name = "insurance_group_number", length = 50)
    private String insuranceGroupNumber;

    @Column(name = "medical_history")
    private String medicalHistory;

    @Column(name = "allergies")
    private String allergies;
}
