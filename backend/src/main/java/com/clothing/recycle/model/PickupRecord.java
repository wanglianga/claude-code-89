package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 上门记录：回收员称重、拍照、初步分类，居民确认积分/捐赠选项 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pickup_records")
public class PickupRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private RecycleOrder order;

    /** 上门称重（kg） */
    @Column(precision = 8, scale = 2)
    private BigDecimal weightKg;

    /** 现场照片相对路径 */
    private String photoPath;

    /** 初步分类（回收员肉眼判定），逗号分隔 */
    @Column(length = 255)
    private String preCategories;

    /** 是否迟到（社区对上门时效有考核） */
    private boolean arrivedLate;

    private LocalDateTime arrivedAt;

    /** 居民确认选项：POINTS 积分 / DONATION 公益捐赠 / MIXED 积分+捐赠 */
    @Column(length = 16)
    private String confirmOption;

    /** 居民是否已签字确认 */
    private boolean residentConfirmed;

    @Column(length = 500)
    private String note;

    private LocalDateTime createdAt = LocalDateTime.now();
}
