package com.example.medical.module.prescription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PrescriptionFormDTO {

    @NotNull(message = "Patient is required")
    private Long patientId;

    @NotNull(message = "Doctor is required")
    private Long doctorId;

    @NotBlank(message = "Diagnosis is required")
    private String diagnosis;

    private String icd10Codes;

    private LocalDate prescriptionDate;

    private String prescriptionType;

    private String rxStatus;

    private String prescriberNpi;

    private String deaNumber;

    private String controlledSchedule;

    private String pharmacyName;

    private String pharmacyPhone;

    private String pharmacyNpi;

    @NotEmpty(message = "Prescription items are required")
    private List<PrescriptionItemDTO> items;
}
