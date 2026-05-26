package com.example.medical.module.system.controller;

import com.example.medical.common.result.Result;
import com.example.medical.module.system.dto.SysMenuFormDTO;
import com.example.medical.module.system.dto.SysMenuVO;
import com.example.medical.module.system.service.SysMenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SysMenuController {

    private final SysMenuService sysMenuService;

    @GetMapping("/tree")
    public Result<List<SysMenuVO>> getTree() {
        return Result.ok(sysMenuService.getTree());
    }

    @GetMapping
    public Result<List<SysMenuVO>> listAll() {
        return Result.ok(sysMenuService.listAll());
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody SysMenuFormDTO dto) {
        sysMenuService.create(dto);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SysMenuFormDTO dto) {
        sysMenuService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysMenuService.delete(id);
        return Result.ok();
    }
}
