package com.example.medical.module.system.repository;

import com.example.medical.module.system.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    List<PasswordHistory> findTop3ByUserTypeAndUserIdOrderByChangedAtDesc(String userType, Long userId);
}
