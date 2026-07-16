package com.example.medical.module.quality.repository;

import com.example.medical.module.quality.entity.QualityResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QualityResultRepository extends JpaRepository<QualityResult, Long> {

    List<QualityResult> findByCmsIdOrderByCalculatedAtDesc(String cmsId);
}
