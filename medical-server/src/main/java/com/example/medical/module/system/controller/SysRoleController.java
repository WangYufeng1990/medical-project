package com.example.medical.module.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.system.dto.SysRoleFormDTO;
import com.example.medical.module.system.dto.SysRoleVO;
import com.example.medical.module.system.service.SysRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @GetMapping
    public Result<PageResult<SysRoleVO>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword) {
        IPage<SysRoleVO> result = sysRoleService.page(page, size, keyword);
        return Result.ok(PageResult.of(result.getTotal(), result.getSize(),
                result.getCurrent(), result.getRecords()));
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody SysRoleFormDTO dto) {
        sysRoleService.create(dto);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SysRoleFormDTO dto) {
        sysRoleService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.delete(id);
        return Result.ok();
    }
}
