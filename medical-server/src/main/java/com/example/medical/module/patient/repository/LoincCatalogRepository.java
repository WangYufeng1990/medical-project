package com.example.medical.module.patient.repository;

import com.example.medical.module.patient.entity.LoincCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoincCatalogRepository extends JpaRepository<LoincCatalog, Long> {

    Optional<LoincCatalog> findByLoincCode(String loincCode);

    List<LoincCatalog> findByPanelParentCode(String panelParentCode);
}
