package com.example.medical.module.patient.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.patient.dto.PatientFormDTO;
import com.example.medical.module.patient.dto.PatientVO;
import com.example.medical.module.patient.entity.Patient;
import com.example.medical.module.patient.mapper.PatientMapper;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientMapper patientMapper;

    public IPage<PatientVO> page(long page, long size, String keyword) {
        LambdaQueryWrapper<Patient> wrapper = new LambdaQueryWrapper<Patient>()
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(Patient::getName, keyword)
                        .or()
                        .like(Patient::getPhone, keyword))
                .orderByDesc(Patient::getCreateTime);

        Page<Patient> pageParam = new Page<>(page, size);
        return patientMapper.selectPage(pageParam, wrapper).convert(PatientVO::fromEntity);
    }

    public PatientVO getById(Long id) {
        Patient patient = patientMapper.selectById(id);
        if (patient == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Patient not found");
        }
        return PatientVO.fromEntity(patient);
    }

    @Transactional
    public void create(PatientFormDTO dto) {
        patientMapper.insert(dto.toEntity());
    }

    @Transactional
    public void update(Long id, PatientFormDTO dto) {
        Patient patient = patientMapper.selectById(id);
        if (patient == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Patient not found");
        }
        dto.applyTo(patient);
        patientMapper.updateById(patient);
    }

    @Transactional
    public void delete(Long id) {
        patientMapper.deleteById(id);
    }
}
