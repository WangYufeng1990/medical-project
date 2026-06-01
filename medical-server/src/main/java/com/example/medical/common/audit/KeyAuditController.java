package com.example.medical.common.audit;

import com.example.medical.common.result.Result;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class KeyAuditController {

    private final KeyAuditRepository keyAuditRepository;

    @GetMapping("/history")
    public Result<List<KeyAudit>> history() {
        return Result.ok(keyAuditRepository.findAllByOrderByEventTimeDesc());
    }
}
