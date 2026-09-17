package com.example.medical.module.appointment.service;

import com.example.medical.common.security.DoctorPatientScopeProvider;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;

/** A doctor is related to every patient they have an appointment with. */
@Component
@RequiredArgsConstructor
public class AppointmentScopeProvider implements DoctorPatientScopeProvider {

    private final AppointmentRepository appointmentRepository;

    @Override
    public Collection<Long> patientIdsFor(Long doctorId) {
        return appointmentRepository.findDistinctPatientIdsByDoctor(doctorId);
    }
}
