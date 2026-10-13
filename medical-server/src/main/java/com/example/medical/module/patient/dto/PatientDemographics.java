package com.example.medical.module.patient.dto;

import java.time.LocalDate;

/**
 * The demographics one ADT message carries, in the shape the patient module
 * accepts them (52.5). The integration module maps its own payload onto this;
 * an absent field stays {@code null} and is applied as "not stated", never as
 * "clear the value".
 */
public record PatientDemographics(String mrn, String name, LocalDate dateOfBirth, String sexAtBirth,
                                  String addressLine1, String city, String state, String zipCode) {
}
