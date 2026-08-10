package com.example.medical.module.patient.dto;

import com.example.medical.module.patient.entity.Immunization;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImmunizationVO {

    private Long id;
    private Long patientId;
    private String vaccineName;
    private String cvxCode;
    private LocalDate administrationDate;
    private String lotNumber;
    private String manufacturer;
    private String doseNumber;
    private String site;
    private String route;
    private String status;
    private Long administeredBy;
    private String notes;
    private LocalDateTime createTime;

    public static ImmunizationVO fromEntity(Immunization i) {
        return new ImmunizationVO(
                i.getId(), i.getPatientId(), i.getVaccineName(), i.getCvxCode(),
                i.getAdministrationDate(), i.getLotNumber(), i.getManufacturer(),
                i.getDoseNumber(), i.getSite(), i.getRoute(), i.getStatus(),
                i.getAdministeredBy(), i.getNotes(), i.getCreateTime());
    }
}
