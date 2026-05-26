package com.example.medical.module.patient.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.medical.common.result.PageResult;
import com.example.medical.common.result.Result;
import com.example.medical.module.appointment.dto.AppointmentVO;
import com.example.medical.module.appointment.entity.Appointment;
import com.example.medical.module.appointment.mapper.AppointmentMapper;
import com.example.medical.module.billing.dto.BillVO;
import com.example.medical.module.billing.entity.Bill;
import com.example.medical.module.billing.mapper.BillMapper;
import com.example.medical.module.patient.dto.PatientVO;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.mapper.PatientMapper;
import com.example.medical.module.prescription.dto.PrescriptionItemVO;
import com.example.medical.module.prescription.dto.PrescriptionVO;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.mapper.PrescriptionItemMapper;
import com.example.medical.module.prescription.mapper.PrescriptionMapper;
import com.example.medical.module.system.mapper.SysUserMapper;
import com.example.medical.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patient/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientPortalController {

    private final PatientMapper patientMapper;
    private final AppointmentMapper appointmentMapper;
    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;
    private final BillMapper billMapper;
    private final SysUserMapper sysUserMapper;

    @GetMapping
    public Result<PatientVO> profile(@AuthenticationPrincipal LoginUser loginUser) {
        Patient patient = patientMapper.selectById(loginUser.getUserId());
        return Result.ok(PatientVO.fromEntity(patient));
    }

    @GetMapping("/appointments")
    public Result<PageResult<AppointmentVO>> myAppointments(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Page<Appointment> pageParam = new Page<>(page, size);
        IPage<Appointment> result = appointmentMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getPatientId, loginUser.getUserId())
                        .orderByDesc(Appointment::getAppointmentTime));
        return Result.ok(PageResult.of(result.getTotal(), result.getSize(),
                result.getCurrent(), result.getRecords().stream().map(this::toAppointmentVO).toList()));
    }

    @GetMapping("/prescriptions")
    public Result<PageResult<PrescriptionVO>> myPrescriptions(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Page<Prescription> pageParam = new Page<>(page, size);
        IPage<Prescription> result = prescriptionMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Prescription>()
                        .eq(Prescription::getPatientId, loginUser.getUserId())
                        .orderByDesc(Prescription::getCreateTime));
        return Result.ok(PageResult.of(result.getTotal(), result.getSize(),
                result.getCurrent(), result.getRecords().stream().map(this::toPrescriptionVO).toList()));
    }

    @GetMapping("/bills")
    public Result<PageResult<BillVO>> myBills(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        Page<Bill> pageParam = new Page<>(page, size);
        IPage<Bill> result = billMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Bill>()
                        .eq(Bill::getPatientId, loginUser.getUserId())
                        .orderByDesc(Bill::getCreateTime));
        return Result.ok(PageResult.of(result.getTotal(), result.getSize(),
                result.getCurrent(), result.getRecords().stream().map(this::toBillVO).toList()));
    }

    private AppointmentVO toAppointmentVO(Appointment a) {
        String doctorName = sysUserMapper.selectById(a.getDoctorId()) != null
                ? sysUserMapper.selectById(a.getDoctorId()).getRealName() : "";
        Patient p = patientMapper.selectById(a.getPatientId());
        return AppointmentVO.fromEntity(a, p != null ? p.getName() : "", doctorName);
    }

    private PrescriptionVO toPrescriptionVO(Prescription p) {
        Patient patient = patientMapper.selectById(p.getPatientId());
        String doctorName = sysUserMapper.selectById(p.getDoctorId()) != null
                ? sysUserMapper.selectById(p.getDoctorId()).getRealName() : "";
        List<PrescriptionItemVO> items = prescriptionItemMapper.selectList(
                        new LambdaQueryWrapper<PrescriptionItem>()
                                .eq(PrescriptionItem::getPrescriptionId, p.getId()))
                .stream().map(PrescriptionItemVO::fromEntity).toList();
        return PrescriptionVO.fromEntity(p, patient != null ? patient.getName() : "",
                doctorName, items);
    }

    private BillVO toBillVO(Bill b) {
        Patient p = patientMapper.selectById(b.getPatientId());
        return BillVO.fromEntity(b, p != null ? p.getName() : "");
    }
}
