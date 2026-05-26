package com.example.medical.module.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.medical.module.appointment.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {
}
