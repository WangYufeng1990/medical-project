package com.example.medical.module.system.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/** The break-glass result: the token plus what the caller needs to explain it. */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EmergencyAccessTokenVO {

    private String token;
    private int expiresInMinutes;
    private Long patientId;

    public static EmergencyAccessTokenVO of(String token, int expiresInMinutes, Long patientId) {
        return new EmergencyAccessTokenVO(token, expiresInMinutes, patientId);
    }
}
