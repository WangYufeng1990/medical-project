package com.example.medical.module.prescription.service;

import com.example.medical.common.security.DoctorPatientScopeProvider;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;

/** A doctor is related to every patient they have prescribed for. */
@Component
@RequiredArgsConstructor
public class PrescriptionScopeProvider implements DoctorPatientScopeProvider {

    private final PrescriptionRepository prescriptionRepository;

    @Override
    public Collection<Long> patientIdsFor(Long doctorId) {
        return prescriptionRepository.findDistinctPatientIdsByDoctor(doctorId);
    }
}
