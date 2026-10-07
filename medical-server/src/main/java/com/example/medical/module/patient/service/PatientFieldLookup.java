package com.example.medical.module.patient.service;

import com.example.medical.common.lookup.PatientLookup;
import com.example.medical.module.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PatientFieldLookup implements PatientLookup {

    private final PatientRepository patientRepository;

    @Override
    public Optional<String> displayName(Long patientId) {
        return patientRepository.findNameById(patientId);
    }

    @Override
    public Optional<String> allergies(Long patientId) {
        return patientRepository.findAllergiesById(patientId);
    }
}
