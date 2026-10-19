package com.example.medical.module.appointment.dto;

import com.example.medical.module.appointment.entity.Appointment;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * What an availability question is entitled to know: this slot is taken, and for how
 * long (R53-02). The conflict lookup used to answer with whole {@code AppointmentVO}
 * rows — patient name, chief complaint, notes — for any doctor's calendar, which was a
 * way around the patient scope every detail endpoint enforces.
 */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConflictSlotVO {

    private LocalDateTime appointmentTime;
    private Integer durationMinutes;

    public static ConflictSlotVO fromEntity(Appointment appointment) {
        return new ConflictSlotVO(appointment.getAppointmentTime(), appointment.getDuration());
    }
}
