package com.example.medical.module.patient.controller;

import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.appointment.dto.AppointmentVO;
import com.example.medical.module.appointment.entity.Appointment;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import com.example.medical.module.billing.dto.BillVO;
import com.example.medical.module.billing.entity.Bill;
import com.example.medical.module.billing.repository.BillRepository;
import com.example.medical.module.patient.dto.PatientVO;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.prescription.dto.PrescriptionItemVO;
import com.example.medical.module.prescription.dto.PrescriptionVO;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.repository.PrescriptionItemRepository;
import com.example.medical.module.prescription.repository.PrescriptionRepository;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.repository.SysUserRepository;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final BillRepository billRepository;
    private final SysUserRepository sysUserRepository;

    @GetMapping
    public Result<PatientVO> profile(@AuthenticationPrincipal LoginUser loginUser) {
        Patient patient = patientRepository.findById(loginUser.getUserId()).orElse(null);
        return Result.ok(patient != null ? PatientVO.fromEntity(patient) : null);
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
