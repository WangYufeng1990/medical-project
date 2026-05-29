package com.example.medical.common.exception;

import com.example.medical.common.enums.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturn401ForUnauthorizedCode() {
        BusinessException ex = new BusinessException(ResultCode.UNAUTHORIZED, "Invalid credentials");
        ResponseEntity<?> response = handler.handleBusinessException(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void shouldReturn404ForNotFoundCode() {
        BusinessException ex = new BusinessException(ResultCode.NOT_FOUND, "Patient not found");
        ResponseEntity<?> response = handler.handleBusinessException(ex);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldReturn409ForConflictCode() {
        BusinessException ex = new BusinessException(ResultCode.CONFLICT, "Username already exists");
        ResponseEntity<?> response = handler.handleBusinessException(ex);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }
}
