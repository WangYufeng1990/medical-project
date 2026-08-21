package com.example.medical.module.prescription.service;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.repository.PharmacyDirectoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * EPCS (electronic prescribing of controlled substances, 21 CFR Part 1311)
 * gate. There is currently NO real transmission channel — no dual-factor auth,
 * no DEA certificate, no Surescripts endpoint. Therefore controlled-substance
 * prescriptions are fail-closed: they can be generated as a draft NCPDP XML
 * but must never be marked as transmitted (Review III C4 / Round 49 note).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EpcsService {

    private final PharmacyDirectoryRepository pharmacyDirectoryRepository;

    public boolean requiresEpcs(Prescription p) {
        return p.getControlledSchedule() != null && !p.getControlledSchedule().isBlank();
    }

    /**
     * Throws unless the prescription is a non-controlled draft that may be
     * generated. Controlled substances are always rejected until a real
     * EPCS channel exists.
     */
    public void assertTransmissionSupported(Prescription p, Long pharmacyId) {
        if (!requiresEpcs(p)) return;
        if (pharmacyId != null) {
            boolean supportsEpcs = pharmacyDirectoryRepository.findById(pharmacyId)
                    .map(ph -> ph.getSupportsEpcs() != null && ph.getSupportsEpcs() == 1)
                    .orElse(false);
            if (!supportsEpcs) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "Selected pharmacy does not support EPCS (electronic prescribing of controlled substances)");
            }
        }
        throw new BusinessException(ResultCode.CONFLICT,
                "EPCS transmission is not implemented (21 CFR Part 1311). Controlled-substance "
                + "prescriptions must not be marked as transmitted.");
    }
}
