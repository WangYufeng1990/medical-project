package com.example.medical.module.appointment.service;

import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.lookup.StaffLookup;
import com.example.medical.module.appointment.dto.AgentAppointmentView;
import com.example.medical.module.appointment.entity.Appointment;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * The read-only appointment surface the agent is allowed to use (Round 58.1).
 * <p>
 * The patient is supplied by the caller from the run it is serving, never by the request; the query is
 * bounded to future appointments within a fixed window and a maximum size, and the returned shape has
 * no clinical text fields.
 */
@Service
@RequiredArgsConstructor
public class AgentAppointmentReadService {

    static final int MAX_ITEMS = 20;
    static final int MAX_DAYS_AHEAD = 365;

    private final AppointmentRepository appointments;
    private final StaffLookup staff;

    public List<AgentAppointmentView> upcoming(Long patientId, ZoneId zone, int limit) {
        int bounded = Math.max(1, Math.min(limit, MAX_ITEMS));
        LocalDateTime now = LocalDateTime.now(zone);
        return appointments.findUpcomingForPatient(patientId, now, now.plusDays(MAX_DAYS_AHEAD),
                        org.springframework.data.domain.PageRequest.of(0, bounded))
                .stream()
                .map(appointment -> view(appointment, zone))
                .toList();
    }

    /** An ID lookup is owned by the run's patient: another patient's appointment does not exist. */
    public AgentAppointmentView one(Long patientId, Long appointmentId, ZoneId zone) {
        Appointment appointment = appointments.findById(appointmentId)
                .filter(found -> patientId.equals(found.getPatientId()))
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "appointment not found"));
        return view(appointment, zone);
    }

    private AgentAppointmentView view(Appointment appointment, ZoneId zone) {
        AgentAppointmentView view = new AgentAppointmentView();
        view.setAppointmentId(appointment.getId());
        view.setStatus(appointment.getStatus());
        view.setStartTime(appointment.getAppointmentTime());
        view.setTimezone(zone.getId());
        view.setDurationMinutes(appointment.getDuration());
        view.setProviderName(staff.realName(appointment.getDoctorId()).orElse(null));
        view.setDepartment(appointment.getDepartment());
        return view;
    }
}
