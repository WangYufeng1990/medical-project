package com.example.medical.module.patient.service;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.patient.dto.PatientDemographics;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes to the master patient record that come from outside the portal — today
 * an ADT feed (52.5). Whoever calls this has already decided that the MRN is
 * unknown ({@link #createFromAdt}) or known ({@link #updateFromAdt}), because
 * resolving the MRN is a read this module answers itself; what it must not do is
 * write the patient's row from the outside, which used to skip these audit rows.
 */
@Service
@RequiredArgsConstructor
public class PatientRegistryService {

    private final PatientRepository patientRepository;

    /** @return the id of the patient just created */
    @Transactional
    @Auditable(module = "patient", action = "CREATE", phiAccess = true)
    public Long createFromAdt(PatientDemographics demographics) {
        Patient patient = new Patient();
        patient.setMrn(demographics.mrn());
        apply(patient, demographics);
        return patientRepository.save(patient).getId();
    }

    @Transactional
    @Auditable(module = "patient", action = "UPDATE", phiAccess = true)
    public void updateFromAdt(Long patientId, PatientDemographics demographics) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Patient not found"));
        apply(patient, demographics);
        patientRepository.save(patient);
    }

    /** An ADT message states what it knows; a null field means "unchanged", not "cleared". */
    private static void apply(Patient patient, PatientDemographics demographics) {
        if (demographics.name() != null) patient.setName(demographics.name());
        if (demographics.dateOfBirth() != null) patient.setDateOfBirth(demographics.dateOfBirth());
        if (demographics.sexAtBirth() != null) patient.setSexAtBirth(demographics.sexAtBirth());
        if (demographics.addressLine1() != null) patient.setAddressLine1(demographics.addressLine1());
        if (demographics.city() != null) patient.setCity(demographics.city());
        if (demographics.state() != null) patient.setState(demographics.state());
        if (demographics.zipCode() != null) patient.setZipCode(demographics.zipCode());
    }
}
