package com.example.medical.module.patient.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.patient.dto.CarePlanVO;
import com.example.medical.module.patient.dto.ImmunizationVO;
import com.example.medical.module.patient.dto.ObservationVO;
import com.example.medical.module.patient.dto.ProblemVO;
import com.example.medical.module.patient.dto.VitalSignVO;
import com.example.medical.module.patient.repository.CarePlanRepository;
import com.example.medical.module.patient.repository.ImmunizationRepository;
import com.example.medical.module.patient.repository.ProblemRepository;
import com.example.medical.module.patient.repository.VitalSignRepository;
import com.example.medical.module.patient.service.LabAnalysisService;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The patient's own clinical record: labs, vitals, problems, immunizations, care plans. */
@RestController
@RequestMapping("/api/v1/patient/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalClinicalController {

    private final LabAnalysisService labAnalysisService;
    private final VitalSignRepository vitalSignRepository;
    private final ProblemRepository problemRepository;
    private final ImmunizationRepository immunizationRepository;
    private final CarePlanRepository carePlanRepository;

    @GetMapping("/observations")
    @Auditable(module = "observation", action = "ACCESS", phiAccess = true)
    public Result<PageResult<ObservationVO>> myObservations(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) String loinc,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(labAnalysisService.pageObservations(loginUser.getUserId(), loinc, page, size));
    }

    @GetMapping("/observations/trend")
    @Auditable(module = "observation", action = "ACCESS", phiAccess = true)
    public Result<List<ObservationVO>> myObservationsTrend(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam String loinc) {
        return Result.ok(labAnalysisService.getTrend(loginUser.getUserId(), loinc));
    }

    @GetMapping("/vitals")
    @Auditable(module = "vital_sign", action = "ACCESS", phiAccess = true)
    public Result<List<VitalSignVO>> myVitals(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(vitalSignRepository.findByPatientIdOrderByRecordedAtDesc(loginUser.getUserId())
                .stream().map(VitalSignVO::fromEntity).toList());
    }

    @GetMapping("/problems")
    @Auditable(module = "problem", action = "ACCESS", phiAccess = true)
    public Result<List<ProblemVO>> myProblems(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(problemRepository.findByPatientIdOrderByOnsetDateDesc(loginUser.getUserId())
                .stream().map(ProblemVO::fromEntity).toList());
    }

    @GetMapping("/immunizations")
    @Auditable(module = "immunization", action = "ACCESS", phiAccess = true)
    public Result<List<ImmunizationVO>> myImmunizations(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(immunizationRepository.findByPatientIdOrderByAdministrationDateDesc(loginUser.getUserId())
                .stream().map(ImmunizationVO::fromEntity).toList());
    }

    @GetMapping("/care-plans")
    @Auditable(module = "care_plan", action = "ACCESS", phiAccess = true)
    public Result<List<CarePlanVO>> myCarePlans(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(carePlanRepository.findByPatientIdOrderByStartDateDesc(loginUser.getUserId())
                .stream().map(CarePlanVO::fromEntity).toList());
    }
}
