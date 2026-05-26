package com.example.medical.module.patient.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.Result;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.mapper.PatientMapper;
import com.example.medical.security.JwtUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/patient")
@RequiredArgsConstructor
public class PatientAuthController {

    private final PatientMapper patientMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @PostMapping("/login")
    public Result<PatientLoginResponse> login(@Valid @RequestBody PatientLoginRequest request) {
        Patient patient = patientMapper.selectOne(new LambdaQueryWrapper<Patient>()
                .eq(Patient::getUsername, request.getUsername()));
        if (patient == null || !passwordEncoder.matches(request.getPassword(), patient.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid username or password");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", List.of("PATIENT"));
        claims.put("patientId", patient.getId());

        String token = jwtUtils.generateToken(patient.getId(), patient.getUsername(), claims);

        return Result.ok(new PatientLoginResponse(token, patient.getId(),
                patient.getName(), patient.getUsername()));
    }

    @Data
    static class PatientLoginRequest {
        @NotBlank private String username;
        @NotBlank private String password;
    }

    @Data
    static class PatientLoginResponse {
        private final String token;
        private final Long patientId;
        private final String name;
        private final String username;
    }
}
