package com.example.medical.module.integration.service;

import com.example.medical.module.integration.dto.AdtEventDTO;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdtService {

    private final PatientRepository patientRepository;

    @Transactional
    public void processAdt(AdtEventDTO event) {
        Patient patient = patientRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("mrn"), event.getPatient().getMrn()),
                org.springframework.data.domain.Sort.unsorted())
                .stream().findFirst()
                .orElseGet(() -> {
                    Patient p = new Patient();
                    p.setMrn(event.getPatient().getMrn());
                    return p;
                });

        AdtEventDTO.PatientInfo info = event.getPatient();
        if (info.getName() != null) patient.setName(info.getName());
        if (info.getDateOfBirth() != null) patient.setDateOfBirth(info.getDateOfBirth());
        if (info.getSexAtBirth() != null) patient.setSexAtBirth(info.getSexAtBirth());
        if (info.getAddress() != null) {
            AdtEventDTO.AddressInfo addr = info.getAddress();
            if (addr.getLine1() != null) patient.setAddressLine1(addr.getLine1());
            if (addr.getCity() != null) patient.setCity(addr.getCity());
            if (addr.getStateCode() != null) patient.setState(addr.getStateCode());
            if (addr.getZip() != null) patient.setZipCode(addr.getZip());
        }

        patientRepository.save(patient);
        log.info("ADT {}: patient mrn={} id={}", event.getEventType(),
                maskMrn(event.getPatient().getMrn()), patient.getId());
    }

    private static String maskMrn(String mrn) {
        if (mrn == null || mrn.length() <= 4) return "****";
        return "****" + mrn.substring(mrn.length() - 4);
    }
}
