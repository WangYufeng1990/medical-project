package com.example.medical.module.patient.dto;

import com.example.medical.common.annotation.PhiField;
import com.example.medical.module.patient.entity.Patient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PatientVO {

    private Long id;
    @PhiField
    private String mrn;
    @PhiField
    private String ssnLast4;
    @PhiField
    private String name;
    @PhiField
    private LocalDate dateOfBirth;
    private String sexAtBirth;
    private String genderIdentity;
    private String race;
    private String ethnicity;
    private String preferredLanguage;
    private String maritalStatus;
    private String patientStatus;
    @PhiField
    private String primaryCareProvider;
    @PhiField
    private String phoneMobile;
    @PhiField
    private String phoneHome;
    @PhiField
    private String phoneWork;
    @PhiField
    private String email;
    @PhiField
    private String addressLine1;
    @PhiField
    private String addressLine2;
    @PhiField
    private String city;
    @PhiField
    private String state;
    @PhiField
    private String zipCode;
    @PhiField
    private String emergencyContactName;
    @PhiField
    private String emergencyContactPhone;
    @PhiField
    private String emergencyContactRelation;
    @PhiField
    private String insurancePayer;
    @PhiField
    private String insuranceMemberId;
    @PhiField
    private String insuranceGroupNumber;
    @PhiField
    private String medicalHistory;
    @PhiField
    private String allergies;
    private LocalDateTime createTime;

    public static PatientVO fromEntity(Patient p) {
        return new PatientVO(
                p.getId(),
                p.getMrn(),
                maskLast4(p.getSsn()),
                p.getName(),
                p.getDateOfBirth(),
                p.getSexAtBirth(),
                p.getGenderIdentity(),
                p.getRace(),
                p.getEthnicity(),
                p.getPreferredLanguage(),
                p.getMaritalStatus(),
                p.getPatientStatus(),
                p.getPrimaryCareProvider(),
                p.getPhoneMobile(),
                p.getPhoneHome(),
                p.getPhoneWork(),
                p.getEmail(),
                p.getAddressLine1(),
                p.getAddressLine2(),
                p.getCity(),
                p.getState(),
                p.getZipCode(),
                p.getEmergencyContactName(),
                p.getEmergencyContactPhone(),
                p.getEmergencyContactRelation(),
                p.getInsurancePayer(),
                p.getInsuranceMemberId(),
                p.getInsuranceGroupNumber(),
                p.getMedicalHistory(),
                p.getAllergies(),
                p.getCreateTime());
    }

    private static String maskLast4(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() <= 4) return "****";
        return "****" + value.substring(value.length() - 4);
    }
}
