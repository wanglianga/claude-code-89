package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 拒收退回后的逐单重新分拣记录：新分类、重量变化、原因与复核照片 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "resort_records")
public class ResortRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 被拒收的原批次 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Batch rejectedBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private RecycleOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User sorter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SortCategory newCategory;

    /** 重新分拣重量 */
    @Column(precision = 8, scale = 2)
    private BigDecimal weightKg;

    /** 与拒收批次重量的差异 */
    @Column(precision = 8, scale = 2)
    private BigDecimal weightDiff;

    @Column(length = 500)
    private String reason;

    /** 重新分拣/复核照片 */
    private String photoPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResortOutcome outcome;

    private LocalDateTime createdAt = LocalDateTime.now();
}
