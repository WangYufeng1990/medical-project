package com.example.medical.module.appointment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.appointment.dto.AppointmentFormDTO;
import com.example.medical.module.appointment.dto.AppointmentVO;
import com.example.medical.module.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<AppointmentVO>> page(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) Integer status) {
        IPage<AppointmentVO> result = appointmentService.page(page, size, status);
        return Result.ok(PageResult.of(result.getTotal(), result.getSize(),
                result.getCurrent(), result.getRecords()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<AppointmentVO> getById(@PathVariable Long id) {
        return Result.ok(appointmentService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<Void> create(@Valid @RequestBody AppointmentFormDTO dto) {
        appointmentService.create(dto);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AppointmentFormDTO dto) {
        appointmentService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        appointmentService.delete(id);
        return Result.ok();
    }
}
