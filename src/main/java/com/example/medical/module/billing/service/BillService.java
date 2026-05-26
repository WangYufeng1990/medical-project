package com.example.medical.module.billing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.billing.dto.BillFormDTO;
import com.example.medical.module.billing.dto.BillVO;
import com.example.medical.module.billing.entity.Bill;
import com.example.medical.module.billing.mapper.BillMapper;
import com.example.medical.module.patient.mapper.PatientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BillService {

    private final BillMapper billMapper;
    private final PatientMapper patientMapper;

    public IPage<BillVO> page(long page, long size, Integer status) {
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
                .eq(status != null, Bill::getStatus, status)
                .orderByDesc(Bill::getCreateTime);

        Page<Bill> pageParam = new Page<>(page, size);
        return billMapper.selectPage(pageParam, wrapper).convert(this::toVO);
    }

    public BillVO getById(Long id) {
        Bill b = billMapper.selectById(id);
        if (b == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Bill not found");
        }
        return toVO(b);
    }

    @Transactional
    public void create(BillFormDTO dto) {
        billMapper.insert(dto.toEntity());
    }

    @Transactional
    public void pay(Long id) {
        Bill b = billMapper.selectById(id);
        if (b == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Bill not found");
        }
        if (b.getStatus() != null && b.getStatus() == 1) {
            throw new BusinessException(ResultCode.CONFLICT, "Bill already paid");
        }
        b.setPaidAmount(b.getAmount());
        b.setStatus(1);
        b.setPayTime(LocalDateTime.now());
        billMapper.updateById(b);
    }

    @Transactional
    public void delete(Long id) {
        billMapper.deleteById(id);
    }

    private BillVO toVO(Bill b) {
        String patientName = patientMapper.selectById(b.getPatientId()) != null
                ? patientMapper.selectById(b.getPatientId()).getName() : "";
        return BillVO.fromEntity(b, patientName);
    }
}
