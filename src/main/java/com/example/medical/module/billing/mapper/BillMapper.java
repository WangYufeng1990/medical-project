package com.example.medical.module.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.medical.module.billing.entity.Bill;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BillMapper extends BaseMapper<Bill> {
}
