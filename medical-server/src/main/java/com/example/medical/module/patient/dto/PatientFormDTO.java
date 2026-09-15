package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.Patient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * Patient create/update payload.
 * <p>
 * The {@code @Size} limits follow from the AES storage rule: the column holds hex ciphertext of {@code 1 version byte + 12 IV + plaintext + 16 GCM tag}, so a {@code VARCHAR(n)} column fits at most {@code n/2 - 29} characters of plaintext. Without the bound, an over-long value reaches the database and surfaces as a 500 instead of a 400.
 */
@Data
public class PatientFormDTO {

    @NotBlank(message = "Name is required")
    @Size(max = 71, message = "Name must be at most 71 characters")
    private String name;

    @NotBlank(message = "MRN is required")
    private String mrn;

    @Size(max = 71, message = "SSN must be at most 71 characters")
    private String ssn;

    private LocalDate dateOfBirth;

    private String sexAtBirth;

    private String genderIdentity;

    private String race;

    private String ethnicity;

    private String preferredLanguage;

    private String maritalStatus;

    private String patientStatus;

    @Size(max = 71, message = "Primary care provider must be at most 71 characters")
    private String primaryCareProvider;

    @Size(max = 71, message = "Phone (mobile) must be at most 71 characters")
    private String phoneMobile;

    @Size(max = 71, message = "Phone (home) must be at most 71 characters")
    private String phoneHome;

    @Size(max = 71, message = "Phone (work) must be at most 71 characters")
    private String phoneWork;

    @Size(max = 121, message = "Email must be at most 121 characters")
    private String email;

    @Size(max = 71, message = "Address line 1 must be at most 71 characters")
    private String addressLine1;

    @Size(max = 71, message = "Address line 2 must be at most 71 characters")
    private String addressLine2;

    @Size(max = 71, message = "City must be at most 71 characters")
    private String city;

    @Size(max = 71, message = "State must be at most 71 characters")
    private String state;

    @Size(max = 71, message = "ZIP code must be at most 71 characters")
    private String zipCode;

    @Size(max = 71, message = "Emergency contact name must be at most 71 characters")
    private String emergencyContactName;

    @Size(max = 71, message = "Emergency contact phone must be at most 71 characters")
    private String emergencyContactPhone;

    private String emergencyContactRelation;

    @Size(max = 71, message = "Insurance payer must be at most 71 characters")
    private String insurancePayer;

    @Size(max = 71, message = "Insurance member id must be at most 71 characters")
    private String insuranceMemberId;

    @Size(max = 71, message = "Insurance group number must be at most 71 characters")
    private String insuranceGroupNumber;

    @Size(max = 1971, message = "Medical history must be at most 1971 characters")
    private String medicalHistory;

    @Size(max = 971, message = "Allergies must be at most 971 characters")
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
