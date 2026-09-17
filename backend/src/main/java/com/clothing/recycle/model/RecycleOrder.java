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

    /** 首次进入的批次（来源批次，拒收再分配后保留以追溯原始流向） */
    @ManyToOne(fetch = FetchType.LAZY)
    private Batch batch;

    /** 当前所在批次（拒收重新分拣后可改指再分配批次；在可入批池时为空） */
    @ManyToOne(fetch = FetchType.LAZY)
    private Batch currentBatch;

    /** 居民要求公示时隐藏个人信息：公示端仅展示批次与去向，不展示单号/小区等住户明细 */
    private boolean publicHidden;

    /** 重量冗余字段，便于统计（来自上门称重） */
    private BigDecimal pickupWeight;

    // ---------- 定向领取按件库存（仅对可直接捐赠、机构已签收后的来源单有意义） ----------
    /** 可分配件数：机构签收时按回收单件数初始化（null 视为尚未初始化，不参与定向发放） */
    private Integer aidAllocatableQuantity;

    /** 已预占件数：分拣匹配生成待领取任务时锁定，签收/取消/退回/换货时按同一明细扣减或释放 */
    private Integer aidReservedQuantity = 0;

    /** 已发放件数：实际签收后累加，只增不减（取消/退回释放的是未签收预占，不冲减已发放） */
    private Integer aidIssuedQuantity = 0;

    public int aidAllocatable() { return aidAllocatableQuantity == null ? 0 : aidAllocatableQuantity; }
    public int aidReserved() { return aidReservedQuantity == null ? 0 : aidReservedQuantity; }
    public int aidIssued() { return aidIssuedQuantity == null ? 0 : aidIssuedQuantity; }

    /** 当前可匹配余额：可分配 - 已预占 - 已发放；未初始化可分配量的回收单余额为 0 */
    public int aidAvailable() { return aidAllocatable() - aidReserved() - aidIssued(); }
}
