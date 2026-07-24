package com.example.medical.module.appointment.repository;

import com.example.medical.module.appointment.entity.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ReferralRepository extends JpaRepository<Referral, Long>, JpaSpecificationExecutor<Referral> {
    List<Referral> findByPatientIdOrderByReferralDateDesc(Long patientId);
}
