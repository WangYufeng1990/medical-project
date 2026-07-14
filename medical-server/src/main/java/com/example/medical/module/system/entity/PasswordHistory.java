package com.example.medical.module.system.entity;

import com.example.medical.common.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "password_history")
@SQLDelete(sql = "UPDATE password_history SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class PasswordHistory extends BaseEntity {

    @Column(name = "user_type", length = 10)
    private String userType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "changed_at")
    private LocalDateTime changedAt;

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) changedAt = LocalDateTime.now();
    }
}
