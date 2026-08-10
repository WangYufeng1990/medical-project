package com.example.medical.module.appointment.dto;

import com.example.medical.module.appointment.entity.Referral;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReferralVO {

    private Long id;
    private Long patientId;
    private Long referringDoctorId;
    private String specialistName;
    private String specialistNpi;
    private String specialty;
    private String diagnosis;
    private String reason;
    private String urgency;
    private String status;
    private LocalDate referralDate;
    private LocalDate appointmentDate;
    private LocalDate completionDate;
    private String notes;
    private LocalDateTime createTime;

    public static ReferralVO fromEntity(Referral r) {
        return new ReferralVO(
                r.getId(), r.getPatientId(), r.getReferringDoctorId(),
                r.getSpecialistName(), r.getSpecialistNpi(), r.getSpecialty(),
                r.getDiagnosis(), r.getReason(), r.getUrgency(), r.getStatus(),
                r.getReferralDate(), r.getAppointmentDate(), r.getCompletionDate(),
                r.getNotes(), r.getCreateTime());
    }
}
