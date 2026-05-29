package com.example.medical.module.prescription.dto;

import com.example.medical.module.prescription.entity.Prescription;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PrescriptionVO {

    private Long id;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private String diagnosis;
    private String icd10Codes;
    private LocalDate prescriptionDate;
    private String prescriptionType;
    private String rxStatus;
    private String prescriberNpi;
    private String controlledSchedule;
    private String pharmacyName;
    private String pharmacyPhone;
    private List<PrescriptionItemVO> items;
    private LocalDateTime createTime;

    public static PrescriptionVO fromEntity(Prescription p, String patientName,
                                            String doctorName, List<PrescriptionItemVO> items) {
        return new PrescriptionVO(
                p.getId(), p.getPatientId(), patientName,
                p.getDoctorId(), doctorName, p.getDiagnosis(),
                p.getIcd10Codes(), p.getPrescriptionDate(),
                p.getPrescriptionType(), p.getRxStatus(),
                p.getPrescriberNpi(), p.getControlledSchedule(),
                p.getPharmacyName(), p.getPharmacyPhone(),
                items, p.getCreateTime());
    }
}
