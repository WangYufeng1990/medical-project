package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.Patient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PatientFormDTO {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Gender is required")
    private Integer gender;

    @NotNull(message = "Age is required")
    private Integer age;

    private String idCard;
    private String phone;
    private String address;
    private String medicalHistory;
    private String allergies;

    public Patient toEntity() {
        Patient p = new Patient();
        p.setName(name);
        p.setGender(gender);
        p.setAge(age);
        p.setIdCard(idCard);
        p.setPhone(phone);
        p.setAddress(address);
        p.setMedicalHistory(medicalHistory);
        p.setAllergies(allergies);
        return p;
    }

    public void applyTo(Patient p) {
        p.setName(name);
        p.setGender(gender);
        p.setAge(age);
        p.setIdCard(idCard);
        p.setPhone(phone);
        p.setAddress(address);
        p.setMedicalHistory(medicalHistory);
        p.setAllergies(allergies);
    }
}
