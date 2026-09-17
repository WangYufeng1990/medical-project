package com.example.medical.module.patient.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.result.Result;
import com.example.medical.module.appointment.service.AppointmentService;
import com.example.medical.module.billing.service.BillService;
import com.example.medical.module.patient.dto.PatientDataExport;
import com.example.medical.module.patient.dto.PatientVO;
import com.example.medical.module.patient.service.PatientService;
import com.example.medical.module.prescription.service.PrescriptionService;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** HIPAA Right of Access export: this patient's demographics and clinical history. */
@RestController
@RequestMapping("/api/v1/patient/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalExportController {

    private final PatientService patientService;
    private final AppointmentService appointmentService;
    private final PrescriptionService prescriptionService;
    private final BillService billService;

    @GetMapping("/export")
    @Auditable(module = "patient", action = "EXPORT_SELF", phiAccess = true)
    public Result<PatientDataExport> exportMyData(@AuthenticationPrincipal LoginUser loginUser) {
        Long patientId = loginUser.getUserId();
        PatientVO demographics = patientService.profile(patientId);
        if (demographics == null) return Result.ok(null);

        return Result.ok(PatientDataExport.of(demographics,
                appointmentService.listForPatient(patientId),
                prescriptionService.listForPatient(patientId),
                billService.listForPatient(patientId)));
    }
}
