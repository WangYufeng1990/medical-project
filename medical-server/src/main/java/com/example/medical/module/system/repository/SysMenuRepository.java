package com.example.medical.module.system.repository;

import com.example.medical.module.system.entity.SysMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {

    List<SysMenu> findAllByOrderBySortAsc();

    long countByParentId(Long parentId);
}
