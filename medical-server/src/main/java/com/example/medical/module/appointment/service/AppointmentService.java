package com.example.medical.module.appointment.service;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.common.security.DoctorPatientScope;
import com.example.medical.module.appointment.dto.AppointmentFormDTO;
import com.example.medical.module.appointment.dto.AppointmentVO;
import com.example.medical.module.appointment.entity.Appointment;
import com.example.medical.module.appointment.repository.AppointmentRepository;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.repository.PatientRepository;
import com.example.medical.module.system.entity.SysUser;
import com.example.medical.module.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final SysUserRepository sysUserRepository;
    private final com.example.medical.module.billing.repository.ChargeRepository chargeRepository;
    private final DoctorPatientScope doctorPatientScope;

    public Page<AppointmentVO> page(long page, long size, Integer status, Long patientId) {
        Set<Long> scopedPatientIds = doctorPatientScope.resolve();
        Specification<Appointment> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (status != null) predicates = cb.and(predicates, cb.equal(root.get("status"), status));
            if (patientId != null) predicates = cb.and(predicates, cb.equal(root.get("patientId"), patientId));
            if (scopedPatientIds != null) {
                predicates = cb.and(predicates, root.get("patientId").in(scopedPatientIds));
            }
            return predicates;
        };
        PageRequest pageable = PageRequest.of((int) (page - 1), (int) size);
        return appointmentRepository.findAll(spec, pageable).map(this::toVO);
    }

    public AppointmentVO getById(Long id) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Appointment not found"));
        doctorPatientScope.requireAccess(a.getPatientId());
        return toVO(a);
    }

    @Transactional
    @Auditable(module = "appointment", action = "CREATE")
    public void create(AppointmentFormDTO dto) {
        if (dto.getAppointmentTime() != null && dto.getAppointmentTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Cannot schedule appointments in the past");
        }
        checkConflict(dto.getDoctorId(), dto.getAppointmentTime(), null);
        appointmentRepository.save(dto.toEntity());
    }

    @Transactional
    @Auditable(module = "appointment", action = "UPDATE")
    public void update(Long id, AppointmentFormDTO dto) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Appointment not found"));
        doctorPatientScope.requireAccess(a.getPatientId());
        if (a.getStatus() != null && java.util.Set.of(2, 3, 4).contains(a.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "Terminal appointments cannot be modified");
        }
        if (dto.getAppointmentTime() != null && dto.getAppointmentTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Cannot schedule appointments in the past");
        }
        checkConflict(dto.getDoctorId(), dto.getAppointmentTime(), id);
        Integer previousStatus = a.getStatus();
        dto.applyTo(a);
        appointmentRepository.save(a);
        if (Integer.valueOf(3).equals(a.getStatus()) && !Integer.valueOf(3).equals(previousStatus)) {
            generateCharge(a);
        }
    }

    private void generateCharge(Appointment a) {
        boolean exists = chargeRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("appointmentId"), a.getId()),
                        cb.equal(root.get("patientId"), a.getPatientId())),
                org.springframework.data.domain.PageRequest.of(0, 1))
                .hasContent();
        if (exists) return;

        com.example.medical.module.billing.entity.Charge c = new com.example.medical.module.billing.entity.Charge();
        c.setPatientId(a.getPatientId());
        c.setAppointmentId(a.getId());
        c.setDoctorId(a.getDoctorId());
        c.setCptCodes(a.getCptCode());
        c.setIcd10Codes(a.getChiefComplaint());
        c.setVisitType(a.getVisitType());
        c.setChargeAmount(a.getCptCode() != null && a.getCptCode().startsWith("992") ? new java.math.BigDecimal("90") : new java.math.BigDecimal("100"));
        c.setStatus("DRAFT");
        chargeRepository.save(c);
    }

    @Transactional
    @Auditable(module = "appointment", action = "DELETE")
    public void delete(Long id) {
        appointmentRepository.deleteById(id);
    }

    public List<AppointmentVO> findConflicts(Long doctorId, LocalDateTime appointmentTime, Long excludeId) {
        LocalDateTime windowStart = appointmentTime.minusMinutes(30);
        LocalDateTime windowEnd = appointmentTime.plusMinutes(30);
        return appointmentRepository
                .findConflicting(doctorId, windowStart, windowEnd)
                .stream()
                .filter(a -> excludeId == null || !a.getId().equals(excludeId))
                .map(this::toVO)
                .toList();
    }

    private void checkConflict(Long doctorId, LocalDateTime appointmentTime, Long excludeId) {
        if (!findConflicts(doctorId, appointmentTime, excludeId).isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "Doctor has a conflicting appointment within 30 minutes of this time");
        }
    }

    private AppointmentVO toVO(Appointment a) {
        String patientName = patientRepository.findById(a.getPatientId())
                .map(Patient::getName).orElse("");
        String doctorName = sysUserRepository.findById(a.getDoctorId())
                .map(SysUser::getRealName).orElse("");
        return AppointmentVO.fromEntity(a, patientName, doctorName);
    }
}
