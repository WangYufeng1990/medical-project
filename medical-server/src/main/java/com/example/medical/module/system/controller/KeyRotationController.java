package com.example.medical.module.system.controller;

import com.example.medical.common.config.AesCryptoUtil;
import com.example.medical.common.job.KeyRotationService;
import com.example.medical.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/rotate")
    public Result<RotationStatus> rotate(@Valid @RequestBody RotateRequest body) {
        if (AesCryptoUtil.isRotationActive() && !keyRotationService.isComplete()) {
            return Result.fail(409, "Rotation already in progress — check /rotation-status for progress");
        }
        AesCryptoUtil.rotate(body.getNewKey(), body.getOldKey());
        keyRotationService.startRuntimeRotation();
        return rotationStatus();
    }

    public record RotationStatus(boolean rotationActive, boolean running, boolean complete,
                                  Map<String, Integer> remainingByTable) {}

    @Data
    static class RotateRequest {
        @NotBlank private String newKey;
        @NotBlank private String oldKey;
    }
}
