package com.example.medical.module.patient.entity;

import com.example.medical.common.base.BaseEntity;
import com.example.medical.common.config.AesAttributeConverter;
import com.example.medical.common.config.LocalDateAttributeConverter;
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
@SQLDelete(sql = "UPDATE patient SET is_deleted = 1 WHERE id = ? AND version = ?")
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

    @Convert(converter = LocalDateAttributeConverter.class)
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

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "primary_care_provider")
    private String primaryCareProvider;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "phone_mobile")
    private String phoneMobile;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "phone_home")
    private String phoneHome;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "phone_work")
    private String phoneWork;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "email")
    private String email;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "address_line1")
    private String addressLine1;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "address_line2")
    private String addressLine2;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "city")
    private String city;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "state")
    private String state;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "zip_code")
    private String zipCode;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_relation", length = 50)
    private String emergencyContactRelation;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "insurance_payer")
    private String insurancePayer;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "insurance_member_id")
    private String insuranceMemberId;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "insurance_group_number")
    private String insuranceGroupNumber;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "medical_history", length = 4000)
    private String medicalHistory;

    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "allergies", length = 2000)
    private String allergies;
}
