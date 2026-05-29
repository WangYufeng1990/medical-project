package com.example.medical.module.prescription.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PrescriptionUpdateFormDTO {

    private String diagnosis;
    private String icd10Codes;
    private LocalDate prescriptionDate;
    private String rxStatus;
    private String pharmacyName;
    private String pharmacyPhone;
    private String pharmacyNpi;
    private List<PrescriptionItemDTO> items;
}
