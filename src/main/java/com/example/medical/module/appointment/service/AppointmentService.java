package com.example.medical.module.appointment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.appointment.dto.AppointmentFormDTO;
import com.example.medical.module.appointment.dto.AppointmentVO;
import com.example.medical.module.appointment.entity.Appointment;
import com.example.medical.module.appointment.mapper.AppointmentMapper;
import com.example.medical.module.patient.mapper.PatientMapper;
import com.example.medical.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentMapper appointmentMapper;
    private final PatientMapper patientMapper;
    private final SysUserMapper sysUserMapper;

    public IPage<AppointmentVO> page(long page, long size, Integer status) {
        LambdaQueryWrapper<Appointment> wrapper = new LambdaQueryWrapper<Appointment>()
                .eq(status != null, Appointment::getStatus, status)
                .orderByDesc(Appointment::getAppointmentTime);

        Page<Appointment> pageParam = new Page<>(page, size);
        return appointmentMapper.selectPage(pageParam, wrapper).convert(this::toVO);
    }

    public AppointmentVO getById(Long id) {
        Appointment a = appointmentMapper.selectById(id);
        if (a == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Appointment not found");
        }
        return toVO(a);
    }

    @Transactional
    public void create(AppointmentFormDTO dto) {
        appointmentMapper.insert(dto.toEntity());
    }

    @Transactional
    public void update(Long id, AppointmentFormDTO dto) {
        Appointment a = appointmentMapper.selectById(id);
        if (a == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Appointment not found");
        }
        dto.applyTo(a);
        appointmentMapper.updateById(a);
    }

    @Transactional
    public void delete(Long id) {
        appointmentMapper.deleteById(id);
    }

    private AppointmentVO toVO(Appointment a) {
        String patientName = patientMapper.selectById(a.getPatientId()) != null
                ? patientMapper.selectById(a.getPatientId()).getName() : "";
        String doctorName = sysUserMapper.selectById(a.getDoctorId()) != null
                ? sysUserMapper.selectById(a.getDoctorId()).getRealName() : "";
        return AppointmentVO.fromEntity(a, patientName, doctorName);
    }
}
