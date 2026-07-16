package com.example.medical.module.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AdtEventPayload {

    @NotBlank
    private String sourceMessageId;

    @NotBlank
    private String eventType;

    private LocalDateTime eventTime;

    private PatientInfo patient;

    private VisitInfo visit;

    @Data
    public static class PatientInfo {
        @NotBlank
        private String mrn;
        private String name;
        private LocalDate dateOfBirth;
        private String sexAtBirth;
        private AddressInfo address;
    }

    @Data
    public static class AddressInfo {
        private String line1;
        private String city;
        @JsonProperty("state")
        private String stateCode;
        private String zip;
    }

    @Data
    public static class VisitInfo {
        private String visitNumber;
        private LocalDateTime admitDate;
        private LocalDateTime dischargeDate;
        private String department;
        private String admittingDoctorNpi;
    }
}
