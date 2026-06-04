package com.example.medical.module.patient.controller;

import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.result.PageResult;
import java.util.Map;
import com.example.medical.common.result.Result;
import com.example.medical.common.validation.ValidPassword;
import com.example.medical.module.appointment.dto.AppointmentVO;
import com.example.medical.module.appointment.entity.Appointment;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import com.example.medical.module.billing.dto.BillVO;
import com.example.medical.module.billing.entity.Bill;
import com.example.medical.module.billing.repository.BillRepository;
import com.example.medical.module.patient.dto.PatientDataExport;
import com.example.medical.module.patient.dto.PatientVO;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.entity.PatientAuth;
import com.example.medical.module.patient.repository.PatientAuthRepository;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.prescription.dto.PrescriptionItemVO;
import com.example.medical.module.prescription.dto.PrescriptionVO;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.repository.PrescriptionItemRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import com.example.medical.module.system.entity.PasswordHistory;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.repository.PasswordHistoryRepository;
import com.example.medical.module.system.repository.SysUserRepository;
import com.example.medical.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/patient/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalController {

    private static final int PASSWORD_HISTORY_LIMIT = 3;

    private final PatientRepository patientRepository;
    private final PatientAuthRepository patientAuthRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final BillRepository billRepository;
    private final SysUserRepository sysUserRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public Result<PatientVO> profile(@AuthenticationPrincipal LoginUser loginUser) {
        Patient patient = patientRepository.findById(loginUser.getUserId()).orElse(null);
        return Result.ok(patient != null ? PatientVO.fromEntity(patient) : null);
    }

    @PutMapping
    public Result<Void> updateProfile(@AuthenticationPrincipal LoginUser loginUser,
                                       @RequestBody Map<String, Object> body) {
        Patient patient = patientRepository.findById(loginUser.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Patient not found"));

        // name changes require staff verification — NOT self-service
        if (body.containsKey("phoneMobile")) patient.setPhoneMobile((String) body.get("phoneMobile"));
        if (body.containsKey("phoneHome")) patient.setPhoneHome((String) body.get("phoneHome"));
        if (body.containsKey("phoneWork")) patient.setPhoneWork((String) body.get("phoneWork"));
        if (body.containsKey("email")) patient.setEmail((String) body.get("email"));
        if (body.containsKey("addressLine1")) patient.setAddressLine1((String) body.get("addressLine1"));
        if (body.containsKey("addressLine2")) patient.setAddressLine2((String) body.get("addressLine2"));
        if (body.containsKey("city")) patient.setCity((String) body.get("city"));
        if (body.containsKey("state")) patient.setState((String) body.get("state"));
        if (body.containsKey("zipCode")) patient.setZipCode((String) body.get("zipCode"));
        if (body.containsKey("emergencyContactName")) patient.setEmergencyContactName((String) body.get("emergencyContactName"));
        if (body.containsKey("emergencyContactPhone")) patient.setEmergencyContactPhone((String) body.get("emergencyContactPhone"));
        if (body.containsKey("emergencyContactRelation")) patient.setEmergencyContactRelation((String) body.get("emergencyContactRelation"));

        patientRepository.save(patient);
        return Result.ok();
    }

    @GetMapping("/appointments")
    public Result<PageResult<AppointmentVO>> myAppointments(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        PageRequest pageable = PageRequest.of((int) (page - 1), (int) size,
                Sort.by(Sort.Direction.DESC, "appointmentTime"));
        var result = appointmentRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), loginUser.getUserId()),
                pageable);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1,
                result.getContent().stream().map(this::toAppointmentVO).toList()));
    }

    @GetMapping("/prescriptions")
    public Result<PageResult<PrescriptionVO>> myPrescriptions(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        PageRequest pageable = PageRequest.of((int) (page - 1), (int) size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        var result = prescriptionRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), loginUser.getUserId()),
                pageable);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1,
                result.getContent().stream().map(this::toPrescriptionVO).toList()));
    }

    @GetMapping("/export")
    @com.example.medical.common.audit.Auditable(module = "patient", action = "EXPORT_SELF", phiAccess = true)
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

    @GetMapping("/bills")
    public Result<PageResult<BillVO>> myBills(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        PageRequest pageable = PageRequest.of((int) (page - 1), (int) size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        var result = billRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("patientId"), loginUser.getUserId()),
                pageable);
        return Result.ok(PageResult.of(result.getTotalElements(), result.getSize(),
                result.getNumber() + 1,
                result.getContent().stream().map(this::toBillVO).toList()));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@AuthenticationPrincipal LoginUser loginUser,
                                       @Valid @RequestBody PatientPasswordChangeRequest request) {
        PatientAuth auth = patientAuthRepository.findByPatientId(loginUser.getUserId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Patient auth not found"));
        if (!passwordEncoder.matches(request.getOldPassword(), auth.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Old password is incorrect");
        }

        if (isInPasswordHistory("PATIENT", auth.getId(), request.getNewPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "New password must not match any of the last " + PASSWORD_HISTORY_LIMIT + " passwords");
        }

        PasswordHistory history = new PasswordHistory();
        history.setUserType("PATIENT");
        history.setUserId(auth.getId());
        history.setPasswordHash(auth.getPassword());
        history.setChangedAt(auth.getPasswordChangedAt());
        passwordHistoryRepository.save(history);

        auth.setPassword(passwordEncoder.encode(request.getNewPassword()));
        auth.setPasswordChangedAt(LocalDateTime.now());
        patientAuthRepository.save(auth);
        return Result.ok();
    }

    private boolean isInPasswordHistory(String userType, Long userId, String plainPassword) {
        List<PasswordHistory> recent = passwordHistoryRepository
                .findTop3ByUserTypeAndUserIdOrderByChangedAtDesc(userType, userId);
        return recent.stream().anyMatch(h -> passwordEncoder.matches(plainPassword, h.getPasswordHash()));
    }

    @Data
    static class PatientPasswordChangeRequest {
        @NotBlank(message = "Old password is required")
        private String oldPassword;
        @NotBlank(message = "New password is required")
        @ValidPassword
        private String newPassword;
    }

    private AppointmentVO toAppointmentVO(Appointment a) {
        String doctorName = sysUserRepository.findById(a.getDoctorId())
                .map(SysUser::getRealName).orElse("");
        String patientName = patientRepository.findById(a.getPatientId())
                .map(Patient::getName).orElse("");
        return AppointmentVO.fromEntity(a, patientName, doctorName);
    }

    private PrescriptionVO toPrescriptionVO(Prescription p) {
        String patientName = patientRepository.findById(p.getPatientId())
                .map(Patient::getName).orElse("");
        String doctorName = sysUserRepository.findById(p.getDoctorId())
                .map(SysUser::getRealName).orElse("");
        List<PrescriptionItemVO> items = prescriptionItemRepository
                .findByPrescriptionId(p.getId())
                .stream().map(PrescriptionItemVO::fromEntity).toList();
        return PrescriptionVO.fromEntity(p, patientName, doctorName, items);
    }

    private BillVO toBillVO(Bill b) {
        String patientName = patientRepository.findById(b.getPatientId())
                .map(Patient::getName).orElse("");
        return BillVO.fromEntity(b, patientName);
    }
}
