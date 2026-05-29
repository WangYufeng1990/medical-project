package com.example.medical.module.appointment.service;

import com.example.medical.common.audit.Auditable;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
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

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final SysUserRepository sysUserRepository;

    public Page<AppointmentVO> page(long page, long size, Integer status) {
        Specification<Appointment> spec = (root, query, cb) -> {
            if (status == null) return null;
            return cb.equal(root.get("status"), status);
        };
        PageRequest pageable = PageRequest.of((int) (page - 1), (int) size);
        return appointmentRepository.findAll(spec, pageable).map(this::toVO);
    }

    public AppointmentVO getById(Long id) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Appointment not found"));
        return toVO(a);
    }

    @Transactional
    @Auditable(module = "appointment", action = "CREATE")
    public void create(AppointmentFormDTO dto) {
        checkConflict(dto.getDoctorId(), dto.getAppointmentTime(), null);
        appointmentRepository.save(dto.toEntity());
    }

    @Transactional
    @Auditable(module = "appointment", action = "UPDATE")
    public void update(Long id, AppointmentFormDTO dto) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Appointment not found"));
        checkConflict(dto.getDoctorId(), dto.getAppointmentTime(), id);
        dto.applyTo(a);
        appointmentRepository.save(a);
    }

    @Transactional
    @Auditable(module = "appointment", action = "DELETE")
    public void delete(Long id) {
        appointmentRepository.deleteById(id);
    }

    private void checkConflict(Long doctorId, LocalDateTime appointmentTime, Long excludeId) {
        LocalDateTime windowStart = appointmentTime.minusMinutes(30);
        LocalDateTime windowEnd = appointmentTime.plusMinutes(30);
        List<Appointment> conflicts = appointmentRepository
                .findConflicting(doctorId, windowStart, windowEnd);
        boolean hasConflict = conflicts.stream()
                .anyMatch(a -> !a.getId().equals(excludeId));
        if (hasConflict) {
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
