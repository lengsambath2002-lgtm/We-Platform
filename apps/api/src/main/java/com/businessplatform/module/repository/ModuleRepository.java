package com.businessplatform.module.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.module.domain.Module;

public interface ModuleRepository extends JpaRepository<Module, String> {

    List<Module> findAllByOrderBySortOrder();
}
