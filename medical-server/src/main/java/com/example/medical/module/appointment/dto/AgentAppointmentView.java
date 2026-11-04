package com.example.medical.module.appointment.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * What the agent may see of an appointment (Round 58.1): identity, status, timing and display-only
 * provider/department fields. Chief complaint, notes, description, diagnoses and codes are absent by
 * construction, so a tool cannot leak them by asking for more fields.
 */
@Data
public class AgentAppointmentView {

    private Long appointmentId;
    private Integer status;
    private LocalDateTime startTime;
    private String timezone;
    private Integer durationMinutes;
    private String providerName;
    private String department;
}
