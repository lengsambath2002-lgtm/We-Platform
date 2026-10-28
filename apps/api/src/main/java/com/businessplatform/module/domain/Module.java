package com.businessplatform.module.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A module in the registry (core or industry), with its availability. */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "module")
public class Module {

    public static final String PROJECTS = "projects";
    public static final String HR = "hr";

    @Id
    private String key;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_km", nullable = false)
    private String nameKm;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String status;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    public boolean available() {
        return "AVAILABLE".equals(status);
    }
}
