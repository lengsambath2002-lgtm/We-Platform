package com.businessplatform.retail.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.retail.domain.ShopLink;

public interface ShopLinkRepository extends JpaRepository<ShopLink, UUID> {

    List<ShopLink> findByOrgIdOrderByBusinessNameAsc(UUID orgId);
}
