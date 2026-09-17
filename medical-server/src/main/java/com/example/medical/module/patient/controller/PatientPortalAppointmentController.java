package com.example.medical.module.patient.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.appointment.dto.AppointmentVO;
import com.example.medical.module.appointment.dto.ReferralVO;
import com.example.medical.module.appointment.service.AppointmentService;
import com.example.medical.module.appointment.service.ReferralService;
import com.example.medical.module.billing.dto.PriorAuthVO;
import com.example.medical.module.billing.service.PriorAuthService;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The patient's own scheduling and the referrals / authorizations attached to it. */
@RestController
@RequestMapping("/api/v1/patient/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalAppointmentController {

    private final AppointmentService appointmentService;
    private final ReferralService referralService;
    private final PriorAuthService priorAuthService;

    @GetMapping("/appointments")
    public Result<PageResult<AppointmentVO>> myAppointments(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        var result = appointmentService.pageForPatient(loginUser.getUserId(), page, size);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, result.getContent()));
    }

    @PutMapping("/appointments/{id}/cancel")
    public Result<Void> cancelMyAppointment(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable Long id) {
        appointmentService.cancelByPatient(id, loginUser.getUserId());
        return Result.ok();
    }

    @GetMapping("/referrals")
    @Auditable(module = "referral", action = "ACCESS", phiAccess = true)
    public Result<List<ReferralVO>> myReferrals(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(referralService.listByPatient(loginUser.getUserId()));
    }

    @GetMapping("/prior-auths")
    @Auditable(module = "prior_auth", action = "ACCESS", phiAccess = true)
    public Result<List<PriorAuthVO>> myPriorAuths(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(priorAuthService.listByPatient(loginUser.getUserId()));
    }
}
