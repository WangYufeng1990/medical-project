package com.example.medical.module.prescription.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PrescriptionItemMapper extends BaseMapper<PrescriptionItem> {
}
