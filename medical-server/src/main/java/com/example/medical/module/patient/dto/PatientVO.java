package com.example.medical.module.patient.dto;

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
    private String mrn;
    private String ssnLast4;
    private String name;
    private LocalDate dateOfBirth;
    private String sexAtBirth;
    private String genderIdentity;
    private String race;
    private String ethnicity;
    private String preferredLanguage;
    private String maritalStatus;
    private String patientStatus;
    private String primaryCareProvider;
    private String phoneMobile;
    private String phoneHome;
    private String phoneWork;
    private String email;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String zipCode;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelation;
    private String insurancePayer;
    private String insuranceMemberId;
    private String insuranceGroupNumber;
    private String medicalHistory;
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
