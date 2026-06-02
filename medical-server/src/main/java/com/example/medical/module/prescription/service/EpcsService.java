package com.example.medical.module.prescription.service;

import com.example.medical.module.prescription.entity.Prescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EpcsService {

    public boolean requiresEpcs(Prescription p) {
        return p.getControlledSchedule() != null && !p.getControlledSchedule().isBlank();
    }

    public void auditEpcsTransmission(Prescription p, Long transmittedBy) {
        if (!requiresEpcs(p)) return;

        log.warn("EPCS TRANSMISSION: prescription={} schedule={} prescriber_npi={} dea=***{} transmitted_by={}",
                p.getId(), p.getControlledSchedule(),
                p.getPrescriberNpi(), maskLast4(p.getDeaNumber()),
                transmittedBy);

        // In production: verify dual-factor auth, check DEA cert, submit to Surescripts
    }

    private static String maskLast4(String value) {
        if (value == null || value.length() <= 4) return "****";
        return value.substring(value.length() - 4);
    }
}
