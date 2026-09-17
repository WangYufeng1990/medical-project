package com.example.medical.module.patient.controller;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.result.Result;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import com.example.medical.module.billing.repository.BillRepository;
import com.example.medical.module.patient.dto.PatientDataExport;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.repository.PrescriptionItemRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HIPAA Right of Access export.
 * <p>
 * The only portal endpoint that still queries other modules' repositories: it
 * assembles {@link PatientDataExport} from appointment, prescription and billing
 * data. M8.6 moves that assembly behind a service.
 */
@RestController
@RequestMapping("/api/v1/patient/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalExportController {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final BillRepository billRepository;

    @GetMapping("/export")
    @Auditable(module = "patient", action = "EXPORT_SELF", phiAccess = true)
    public Result<PatientDataExport> exportMyData(@AuthenticationPrincipal LoginUser loginUser) {
        Long patientId = loginUser.getUserId();
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient == null) return Result.ok(null);

        var appointments = appointmentRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), patientId),
                Sort.by(Sort.Direction.DESC, "appointmentTime"));

        var prescriptions = prescriptionRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), patientId),
                Sort.by(Sort.Direction.DESC, "prescriptionDate"));
        List<Long> rxIds = prescriptions.stream().map(Prescription::getId).toList();
        List<PrescriptionItem> allItems = rxIds.isEmpty() ? List.of()
                : prescriptionItemRepository.findAll(
                        (root, query, cb) -> root.get("prescriptionId").in(rxIds));

        var bills = billRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), patientId),
                Sort.by(Sort.Direction.DESC, "createTime"));

        return Result.ok(PatientDataExport.of(patient, appointments, prescriptions,
                allItems, bills));
    }
}
