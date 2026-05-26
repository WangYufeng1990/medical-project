package com.example.medical.module.appointment.dto;

import com.example.medical.module.appointment.entity.Appointment;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AppointmentVO {

    private Long id;
    private Long patientId;
    private String patientName;
    private Long doctorId;
    private String doctorName;
    private LocalDateTime appointmentTime;
    private Integer status;
    private String description;
    private String notes;
    private LocalDateTime createTime;

    public static AppointmentVO fromEntity(Appointment a, String patientName, String doctorName) {
        return new AppointmentVO(a.getId(), a.getPatientId(), patientName,
                a.getDoctorId(), doctorName, a.getAppointmentTime(), a.getStatus(),
                a.getDescription(), a.getNotes(), a.getCreateTime());
    }
}
