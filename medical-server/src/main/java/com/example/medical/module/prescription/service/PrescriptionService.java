package com.example.medical.module.prescription.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.medical.common.enums.ResultCode;
import com.example.medical.common.exception.BusinessException;
import com.example.medical.module.patient.mapper.PatientMapper;
import com.example.medical.module.prescription.dto.PrescriptionFormDTO;
import com.example.medical.module.prescription.dto.PrescriptionItemVO;
import com.example.medical.module.prescription.dto.PrescriptionVO;
import com.example.medical.module.prescription.entity.Prescription;
import com.example.medical.module.prescription.entity.PrescriptionItem;
import com.example.medical.module.prescription.mapper.PrescriptionItemMapper;
import com.example.medical.module.prescription.mapper.PrescriptionMapper;
import com.example.medical.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;
    private final PatientMapper patientMapper;
    private final SysUserMapper sysUserMapper;

    public IPage<PrescriptionVO> page(long page, long size) {
        Page<Prescription> pageParam = new Page<>(page, size);
        IPage<Prescription> result = prescriptionMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Prescription>().orderByDesc(Prescription::getCreateTime));
        return result.convert(this::toVO);
    }

    public PrescriptionVO getById(Long id) {
        Prescription p = prescriptionMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Prescription not found");
        }
        return toVO(p);
    }

    @Transactional
    public void create(PrescriptionFormDTO dto) {
        Prescription p = new Prescription();
        p.setPatientId(dto.getPatientId());
        p.setDoctorId(dto.getDoctorId());
        p.setDiagnosis(dto.getDiagnosis());
        p.setPrescriptionDate(dto.getPrescriptionDate() != null
                ? dto.getPrescriptionDate() : LocalDate.now());
        prescriptionMapper.insert(p);

        if (dto.getItems() != null) {
            dto.getItems().forEach(itemDTO -> {
                PrescriptionItem item = itemDTO.toEntity();
                item.setPrescriptionId(p.getId());
                prescriptionItemMapper.insert(item);
            });
        }
    }

    @Transactional
    public void delete(Long id) {
        prescriptionItemMapper.delete(new LambdaQueryWrapper<PrescriptionItem>()
                .eq(PrescriptionItem::getPrescriptionId, id));
        prescriptionMapper.deleteById(id);
    }

    private PrescriptionVO toVO(Prescription p) {
        String patientName = patientMapper.selectById(p.getPatientId()) != null
                ? patientMapper.selectById(p.getPatientId()).getName() : "";
        String doctorName = sysUserMapper.selectById(p.getDoctorId()) != null
                ? sysUserMapper.selectById(p.getDoctorId()).getRealName() : "";
        List<PrescriptionItemVO> items = prescriptionItemMapper.selectList(
                        new LambdaQueryWrapper<PrescriptionItem>()
                                .eq(PrescriptionItem::getPrescriptionId, p.getId()))
                .stream().map(PrescriptionItemVO::fromEntity).toList();
        return PrescriptionVO.fromEntity(p, patientName, doctorName, items);
    }
}
