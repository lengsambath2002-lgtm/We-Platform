package com.businessplatform.business.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.business.domain.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndOrgId(UUID id, UUID orgId);

    List<Product> findByOrgIdOrderByNameEnAsc(UUID orgId);

    boolean existsByOrgIdAndNameEnIgnoreCaseAndIdNot(UUID orgId, String nameEn, UUID id);
}
