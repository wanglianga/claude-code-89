package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 定向领取任务的来源单明细：匹配时按来源回收单逐单预占件数，
 * 签收/取消/换货/退回重新分拣/转其他家庭均按此明细扣减或释放，
 * 保证同一来源衣物不会被跨家庭超额发放。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "aid_distribution_items")
public class AidDistributionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AidDistribution distribution;

    /** 来源回收单（库存按此单锁定） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private RecycleOrder order;

    /** 本任务从该来源单预占的件数 */
    @Column(nullable = false)
    private Integer reservedQuantity = 0;

    /** 其中已签收发放的件数（签收时从预占结转） */
    @Column(nullable = false)
    private Integer distributedQuantity = 0;

    private LocalDateTime createdAt = LocalDateTime.now();
}
