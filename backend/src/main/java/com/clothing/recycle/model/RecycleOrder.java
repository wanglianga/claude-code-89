package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 旧衣回收单：居民提交预约，贯穿 回收员→分拣中心→公益机构/再生→财务积分 全流程 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "recycle_orders")
public class RecycleOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 32)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User resident;

    @ManyToOne(fetch = FetchType.LAZY)
    private User collector;

    /** 小区 */
    @Column(nullable = false, length = 100)
    private String communityName;

    // ---------- 预约信息（居民填写） ----------
    /** 衣物数量（件） */
    private Integer itemCount;

    /** 品类，逗号分隔：上衣/裤装/外套/鞋包/被褥/其他 */
    @Column(length = 255)
    private String categories;

    /** 是否已清洗 */
    private boolean washed;

    /** 是否含鞋包被褥 */
    private boolean hasShoesBagsBedding;

    @Column(nullable = false, length = 255)
    private String address;

    /** 可预约时段，如 2026-09-17 上午 09:00-11:00 */
    @Column(nullable = false, length = 64)
    private String timeSlot;

    /** 是否希望捐赠（否则倾向积分） */
    private boolean donateWanted;

    /** 关联学校/企业公益活动（可选） */
    @ManyToOne(fetch = FetchType.LAZY)
    private Partner partner;

    // ---------- 流程状态 ----------
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime assignedAt;
    private LocalDateTime pickedAt;
    private LocalDateTime sortedAt;

    /** 所属公示批次（分拣复核完成后入批） */
    @ManyToOne(fetch = FetchType.LAZY)
    private Batch batch;

    /** 重量冗余字段，便于统计（来自上门称重） */
    private BigDecimal pickupWeight;
}
