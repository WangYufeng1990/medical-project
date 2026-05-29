package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.Patient;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PatientFormDTO {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "MRN is required")
    private String mrn;

    private String ssn;

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

    public Patient toEntity() {
        Patient p = new Patient();
        p.setName(name);
        p.setMrn(mrn);
        p.setSsn(ssn);
        p.setDateOfBirth(dateOfBirth);
        p.setSexAtBirth(sexAtBirth);
        p.setGenderIdentity(genderIdentity);
        p.setRace(race);
        p.setEthnicity(ethnicity);
        p.setPreferredLanguage(preferredLanguage);
        p.setMaritalStatus(maritalStatus);
        p.setPatientStatus(patientStatus);
        p.setPrimaryCareProvider(primaryCareProvider);
        p.setPhoneMobile(phoneMobile);
        p.setPhoneHome(phoneHome);
        p.setPhoneWork(phoneWork);
        p.setEmail(email);
        p.setAddressLine1(addressLine1);
        p.setAddressLine2(addressLine2);
        p.setCity(city);
        p.setState(state);
        p.setZipCode(zipCode);
        p.setEmergencyContactName(emergencyContactName);
        p.setEmergencyContactPhone(emergencyContactPhone);
        p.setEmergencyContactRelation(emergencyContactRelation);
        p.setInsurancePayer(insurancePayer);
        p.setInsuranceMemberId(insuranceMemberId);
        p.setInsuranceGroupNumber(insuranceGroupNumber);
        p.setMedicalHistory(medicalHistory);
        p.setAllergies(allergies);
        return p;
    }

    public void applyTo(Patient p) {
        p.setName(name);
        p.setMrn(mrn);
        p.setSsn(ssn);
        p.setDateOfBirth(dateOfBirth);
        p.setSexAtBirth(sexAtBirth);
        p.setGenderIdentity(genderIdentity);
        p.setRace(race);
        p.setEthnicity(ethnicity);
        p.setPreferredLanguage(preferredLanguage);
        p.setMaritalStatus(maritalStatus);
        p.setPatientStatus(patientStatus);
        p.setPrimaryCareProvider(primaryCareProvider);
        p.setPhoneMobile(phoneMobile);
        p.setPhoneHome(phoneHome);
        p.setPhoneWork(phoneWork);
        p.setEmail(email);
        p.setAddressLine1(addressLine1);
        p.setAddressLine2(addressLine2);
        p.setCity(city);
        p.setState(state);
        p.setZipCode(zipCode);
        p.setEmergencyContactName(emergencyContactName);
        p.setEmergencyContactPhone(emergencyContactPhone);
        p.setEmergencyContactRelation(emergencyContactRelation);
        p.setInsurancePayer(insurancePayer);
        p.setInsuranceMemberId(insuranceMemberId);
        p.setInsuranceGroupNumber(insuranceGroupNumber);
        p.setMedicalHistory(medicalHistory);
        p.setAllergies(allergies);
    }
}
