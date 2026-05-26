package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.Patient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PatientVO {

    private Long id;
    private String username;
    private String name;
    private Integer gender;
    private Integer age;
    private String idCard;
    private String phone;
    private String address;
    private String medicalHistory;
    private String allergies;
    private LocalDateTime createTime;

    public static PatientVO fromEntity(Patient p) {
        return new PatientVO(p.getId(), p.getUsername(), p.getName(), p.getGender(),
                p.getAge(), p.getIdCard(), p.getPhone(), p.getAddress(),
                p.getMedicalHistory(), p.getAllergies(), p.getCreateTime());
    }
}
