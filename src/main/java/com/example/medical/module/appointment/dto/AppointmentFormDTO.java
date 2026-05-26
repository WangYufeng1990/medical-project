package com.example.medical.module.appointment.dto;

import com.example.medical.module.appointment.entity.Appointment;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentFormDTO {

    @NotNull(message = "Patient is required")
    private Long patientId;

    @NotNull(message = "Doctor is required")
    private Long doctorId;

    @NotNull(message = "Appointment time is required")
    private LocalDateTime appointmentTime;

    private String description;
    private String notes;
    private Integer status;

    public Appointment toEntity() {
        Appointment a = new Appointment();
        a.setPatientId(patientId);
        a.setDoctorId(doctorId);
        a.setAppointmentTime(appointmentTime);
        a.setDescription(description);
        a.setNotes(notes);
        a.setStatus(status != null ? status : 0);
        return a;
    }

    public void applyTo(Appointment a) {
        a.setPatientId(patientId);
        a.setDoctorId(doctorId);
        a.setAppointmentTime(appointmentTime);
        a.setDescription(description);
        a.setNotes(notes);
        a.setStatus(status);
    }
}
