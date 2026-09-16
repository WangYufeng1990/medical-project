package com.example.medical.module.patient.service;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.patient.entity.PatientAuth;
import com.example.medical.module.patient.repository.PatientAuthRepository;
import com.example.medical.module.system.service.PasswordHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Portal self-service credentials for a patient (a {@code PatientAuth} row). */
@Service
@RequiredArgsConstructor
public class PatientAccountService {

    private final PatientAuthRepository patientAuthRepository;
    private final PasswordHistoryService passwordHistoryService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    @Auditable(module = "auth", action = "PATIENT_PASSWORD_CHANGE", phiAccess = true)
    public void changePassword(Long patientId, String oldPassword, String newPassword) {
        PatientAuth auth = patientAuthRepository.findByPatientId(patientId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Patient auth not found"));
        if (!passwordEncoder.matches(oldPassword, auth.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Old password is incorrect");
        }
        // History is keyed by the credential row id, not the patient id.
        passwordHistoryService.requireNotReused(PasswordHistoryService.PATIENT_USER_TYPE, auth.getId(),
                newPassword);
        passwordHistoryService.record(PasswordHistoryService.PATIENT_USER_TYPE, auth.getId(),
                auth.getPassword(), auth.getPasswordChangedAt());

        auth.setPassword(passwordEncoder.encode(newPassword));
        auth.setPasswordChangedAt(LocalDateTime.now());
        patientAuthRepository.save(auth);
    }
}
