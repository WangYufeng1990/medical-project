package com.example.medical.module.appointment.service;

import com.example.medical.module.appointment.dto.ReferralVO;
import com.example.medical.module.appointment.repository.ReferralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReferralService {

    private final ReferralRepository referralRepository;

    /** Referrals of one patient. Visibility (doctor scope vs. the patient's own token) is the caller's concern. */
    public List<ReferralVO> listByPatient(Long patientId) {
        return referralRepository.findByPatientIdOrderByReferralDateDesc(patientId)
                .stream().map(ReferralVO::fromEntity).toList();
    }
}
