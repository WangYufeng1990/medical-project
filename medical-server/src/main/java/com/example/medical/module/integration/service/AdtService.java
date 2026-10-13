package com.example.medical.module.integration.service;

import com.example.medical.common.lookup.PatientLookup;
import com.example.medical.module.integration.dto.AdtEventPayload;
import com.example.medical.module.patient.dto.PatientDemographics;
import com.example.medical.module.patient.service.PatientRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirth ADT intake. The patient row itself is written by the patient module (52.5). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdtService {

    private final PatientLookup patientLookup;
    private final PatientRegistryService patientRegistry;

    @Transactional
    @com.example.medical.common.audit.Auditable(module = "integration", action = "ADT_UPSERT")
    public void processAdt(AdtEventPayload event) {
        PatientDemographics demographics = toDemographics(event.getPatient());
        Long patientId = patientLookup.idByMrn(demographics.mrn()).orElse(null);
        if (patientId == null) {
            patientId = patientRegistry.createFromAdt(demographics);
        } else {
            patientRegistry.updateFromAdt(patientId, demographics);
        }

        log.info("ADT {}: patient mrn={} id={}", event.getEventType(),
                maskMrn(demographics.mrn()), patientId);
    }

    private static PatientDemographics toDemographics(AdtEventPayload.PatientInfo info) {
        AdtEventPayload.AddressInfo address = info.getAddress();
        return new PatientDemographics(info.getMrn(), info.getName(), info.getDateOfBirth(),
                info.getSexAtBirth(),
                address != null ? address.getLine1() : null,
                address != null ? address.getCity() : null,
                address != null ? address.getStateCode() : null,
                address != null ? address.getZip() : null);
    }

    private static String maskMrn(String mrn) {
        if (mrn == null || mrn.length() <= 4) return "****";
        return "****" + mrn.substring(mrn.length() - 4);
    }
}
