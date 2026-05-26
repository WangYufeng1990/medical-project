package com.example.medical.module.patient.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.medical.module.patient.entity.Patient;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PatientMapper extends BaseMapper<Patient> {
}
