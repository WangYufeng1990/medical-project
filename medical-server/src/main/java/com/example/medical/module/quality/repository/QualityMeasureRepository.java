package com.example.medical.module.quality.repository;

import com.example.medical.module.quality.entity.QualityMeasure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QualityMeasureRepository extends JpaRepository<QualityMeasure, Long> {

    Optional<QualityMeasure> findByCmsId(String cmsId);
}
