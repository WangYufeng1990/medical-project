package com.example.medical.module.system.controller;

import com.example.medical.common.config.AesCryptoUtil;
import com.example.medical.common.job.KeyRotationService;
import com.example.medical.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class KeyRotationController {

    private final KeyRotationService keyRotationService;

    @GetMapping("/rotation-status")
    public Result<RotationStatus> rotationStatus() {
        return Result.ok(new RotationStatus(
                AesCryptoUtil.isRotationActive(),
                keyRotationService.isRunning(),
                keyRotationService.isComplete(),
                keyRotationService.getRemainingByTable()));
    }

    public record RotationStatus(boolean rotationActive, boolean running, boolean complete,
                                  Map<String, Integer> remainingByTable) {}
}
