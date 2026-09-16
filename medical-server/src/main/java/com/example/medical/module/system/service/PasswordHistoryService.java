package com.example.medical.module.system.service;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.system.entity.PasswordHistory;
import com.example.medical.module.system.repository.PasswordHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The single home for password-reuse policy. Both credential owners (staff
 * {@code SysUser} and portal {@code PatientAuth}) keep rows in the same
 * {@code password_history} table, separated by {@code userType}; a caller's
 * {@code userId} is the id of its own credential row, not a patient or user id.
 */
@Service
@RequiredArgsConstructor
public class PasswordHistoryService {

    public static final int LIMIT = 3;
    public static final String STAFF_USER_TYPE = "SYS_USER";
    public static final String PATIENT_USER_TYPE = "PATIENT";

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    /** Throws when {@code rawPassword} matches one of the last {@link #LIMIT} hashes. */
    public void requireNotReused(String userType, Long userId, String rawPassword) {
        if (isReused(userType, userId, rawPassword)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "New password must not match any of the last " + LIMIT + " passwords");
        }
    }

    private boolean isReused(String userType, Long userId, String rawPassword) {
        // Keep in sync with LIMIT: the repository query is a fixed Top3.
        List<PasswordHistory> recent = passwordHistoryRepository
                .findTop3ByUserTypeAndUserIdOrderByChangedAtDesc(userType, userId);
        return recent.stream().anyMatch(h -> passwordEncoder.matches(rawPassword, h.getPasswordHash()));
    }

    /** Records the hash being replaced, so it can no longer be reused. */
    public void record(String userType, Long userId, String replacedHash, LocalDateTime changedAt) {
        PasswordHistory history = new PasswordHistory();
        history.setUserType(userType);
        history.setUserId(userId);
        history.setPasswordHash(replacedHash);
        history.setChangedAt(changedAt);
        passwordHistoryRepository.save(history);
    }
}
