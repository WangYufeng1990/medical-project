package com.example.medical.module.patient.service;

import com.example.medical.module.patient.repository.PatientAuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * The patient side of account lockout (R53-05), in its own transaction — the staff
 * side has done this since {@code LockoutService}. A failure counter has to survive
 * the authentication exception that ends the attempt, and a write inside the login's
 * own transaction is rolled back by that very exception: five wrong passwords were
 * answered with 401 and left {@code failed_attempts} at 0, so the lock never engaged.
 */
@Service
@RequiredArgsConstructor
public class PatientLockoutService {

    private static final int LOCK_DURATION_MINUTES = 15;

    private final PatientAuthRepository patientAuthRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(Long patientAuthId) {
        patientAuthRepository.incrementFailedAttempts(patientAuthId,
                LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
    }

    /**
     * Clears the counter and stamps the login in one update. Resetting through the
     * bulk update and then saving the loaded entity back would undo the reset: the
     * entity still carries the attempts it was read with.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetFailedAttempts(Long patientAuthId) {
        patientAuthRepository.resetFailedAttempts(patientAuthId, LocalDateTime.now());
    }
}
