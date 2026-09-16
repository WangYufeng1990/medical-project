package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.Patient;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Patient self-service profile update.
 * <p>
 * Only the fields a patient is allowed to change. Legal name, MRN, date of birth,
 * sex at birth, insurance and allergies require staff verification, so they are
 * not part of this payload at all — the endpoint used to take a
 * {@code Map<String, Object>} and silently ignore them.
 * <p>
 * The {@code @Size} limits follow from the AES storage rule: the column holds hex
 * ciphertext of {@code 1 version byte + 12 IV + plaintext + 16 GCM tag}, so a
 * {@code VARCHAR(n)} column fits at most {@code n/2 - 29} characters of plaintext.
 * Without the bound, an over-long value reaches the database and surfaces as a 500
 * instead of a 400. ({@code emergencyContactRelation} is not encrypted, so it is
 * bounded by its own {@code VARCHAR(50)}.)
 */
@Data
public class PatientSelfUpdateFormDTO {

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

    @Size(max = 50, message = "Emergency contact relation must be at most 50 characters")
    private String emergencyContactRelation;

    /**
     * Full update: a field omitted from the payload is cleared, which is what PUT
     * means. The portal always submits all twelve.
     */
    public void applyTo(Patient p) {
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
    }
}
