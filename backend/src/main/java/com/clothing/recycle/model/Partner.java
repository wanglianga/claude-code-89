package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 企业公益合作 / 学校捐衣活动 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "partners")
public class Partner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PartnerType type;

    @Column(length = 120)
    private String projectName;

    private String contactName;
    private String contactPhone;

    private LocalDate startDate;
    private LocalDate endDate;

    /** 目标募集重量 kg */
    @Column(precision = 10, scale = 2)
    private BigDecimal targetKg = BigDecimal.ZERO;

    /** 已募集重量 kg */
    @Column(precision = 10, scale = 2)
    private BigDecimal collectedKg = BigDecimal.ZERO;

    /** ACTIVE / CLOSED */
    @Column(length = 16)
    private String status = "ACTIVE";

    @Column(length = 500)
    private String description;

    private LocalDateTime createdAt = LocalDateTime.now();
}
