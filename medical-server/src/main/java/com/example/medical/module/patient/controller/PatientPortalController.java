package com.example.medical.module.patient.controller;

import com.example.medical.common.audit.AuditLogVO;
import com.example.medical.common.audit.Auditable;
import com.example.medical.common.audit.repository.AuditLogRepository;
import com.example.medical.common.base.Pages;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.appointment.dto.AppointmentVO;
import com.example.medical.module.appointment.dto.ReferralVO;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import com.example.medical.module.appointment.service.AppointmentService;
import com.example.medical.module.appointment.service.ReferralService;
import com.example.medical.module.billing.dto.BillVO;
import com.example.medical.module.billing.dto.PriorAuthVO;
import com.example.medical.module.billing.repository.BillRepository;
import com.example.medical.module.billing.service.BillService;
import com.example.medical.module.billing.service.PriorAuthService;
import com.example.medical.module.patient.dto.CarePlanVO;
import com.example.medical.module.patient.dto.ImmunizationVO;
import com.example.medical.module.patient.dto.ObservationVO;
import com.example.medical.module.patient.dto.PatientDataExport;
import com.example.medical.module.patient.dto.PatientPasswordChangeFormDTO;
import com.example.medical.module.patient.dto.PatientPayBillFormDTO;
import com.example.medical.module.patient.dto.PatientSelfUpdateFormDTO;
import com.example.medical.module.patient.dto.PatientVO;
import com.example.medical.module.patient.dto.ProblemVO;
import com.example.medical.module.patient.dto.VitalSignVO;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.CarePlanRepository;
import com.example.medical.module.patient.repository.ImmunizationRepository;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.patient.repository.ProblemRepository;
import com.example.medical.module.patient.repository.VitalSignRepository;
import com.example.medical.module.patient.service.LabAnalysisService;
import com.example.medical.module.patient.service.PatientAccountService;
import com.example.medical.module.patient.service.PatientService;
import com.example.medical.module.prescription.dto.PrescriptionVO;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.repository.PrescriptionItemRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import com.example.medical.module.prescription.service.PrescriptionService;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patient/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalController {

    private final PatientService patientService;
    private final PatientAccountService patientAccountService;
    private final LabAnalysisService labAnalysisService;
    private final AppointmentService appointmentService;
    private final PrescriptionService prescriptionService;
    private final BillService billService;
    private final ReferralService referralService;
    private final PriorAuthService priorAuthService;
    private final VitalSignRepository vitalSignRepository;
    private final ProblemRepository problemRepository;
    private final ImmunizationRepository immunizationRepository;
    private final CarePlanRepository carePlanRepository;
    private final AuditLogRepository auditLogRepository;
    // Only /export still reaches across modules; M8.6 moves that assembly into a service.
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final BillRepository billRepository;

    @GetMapping
    public Result<PatientVO> profile(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(patientService.profile(loginUser.getUserId()));
    }

    @PutMapping
    public Result<Void> updateProfile(@AuthenticationPrincipal LoginUser loginUser,
                                      @Valid @RequestBody PatientSelfUpdateFormDTO form) {
        patientService.updateOwnProfile(loginUser.getUserId(), form);
        return Result.ok();
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                       @Valid @RequestBody PatientPasswordChangeFormDTO form) {
        patientAccountService.changePassword(loginUser.getUserId(), form.getOldPassword(), form.getNewPassword());
        return Result.ok();
    }

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

    @GetMapping("/prescriptions")
    public Result<PageResult<PrescriptionVO>> myPrescriptions(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        var result = prescriptionService.pageForPatient(loginUser.getUserId(), page, size);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, result.getContent()));
    }

    @GetMapping("/referrals")
    @Auditable(module = "referral", action = "ACCESS", phiAccess = true)
    public Result<List<ReferralVO>> myReferrals(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(referralService.listByPatient(loginUser.getUserId()));
    }

    @GetMapping("/bills")
    public Result<PageResult<BillVO>> myBills(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        var result = billService.pageForPatient(loginUser.getUserId(), page, size);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, result.getContent()));
    }

    @PutMapping("/bills/{id}/pay")
    public Result<Void> payMyBill(@AuthenticationPrincipal LoginUser loginUser,
                                  @PathVariable Long id,
                                  @Valid @RequestBody PatientPayBillFormDTO form) {
        billService.payByPatient(id, loginUser.getUserId(), form.getPaymentAmount(), form.getPaymentMethod());
        return Result.ok();
    }

    @GetMapping("/prior-auths")
    @Auditable(module = "prior_auth", action = "ACCESS", phiAccess = true)
    public Result<List<PriorAuthVO>> myPriorAuths(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.ok(priorAuthService.listByPatient(loginUser.getUserId()));
    }

    @GetMapping("/disclosures")
    public Result<PageResult<AuditLogVO>> myDisclosures(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = Pages.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        var result = auditLogRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), loginUser.getUserId()),
                pageable);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1, result.getContent().stream().map(AuditLogVO::fromEntity).toList()));
    }

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
