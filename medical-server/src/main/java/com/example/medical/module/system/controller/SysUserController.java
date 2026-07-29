package com.example.medical.module.system.controller;

import org.springframework.data.domain.Page;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.system.dto.SysUserFormDTO;
import com.example.medical.module.system.dto.SysUserVO;
import com.example.medical.module.system.service.SysUserService;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;
    private final com.example.medical.module.system.repository.SysUserRepository sysUserRepository;

    @GetMapping("/doctors")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<java.util.List<java.util.Map<String, Object>>> doctors() {
        var list = sysUserRepository.findDoctors().stream()
                .map(u -> java.util.Map.<String, Object>of("id", u.getId(), "username", u.getUsername(), "realName", u.getRealName() != null ? u.getRealName() : u.getUsername()))
                .toList();
        return Result.ok(list);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<SysUserVO>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword) {
        Page<SysUserVO> result = sysUserService.page(page, size, keyword);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, result.getContent()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysUserVO> getById(@PathVariable Long id) {
        return Result.ok(sysUserService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> create(@Valid @RequestBody SysUserFormDTO dto) {
        sysUserService.create(dto);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SysUserFormDTO dto) {
        sysUserService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id,
                                @AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser.getUserId().equals(id)) {
            throw new com.example.medical.common.exception.BusinessException(
                    com.example.medical.common.enums.ResultCode.CONFLICT, "Cannot delete your own account");
        }
        sysUserService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> unlock(@PathVariable Long id) {
        sysUserService.unlock(id);
        return Result.ok();
    }
}
