package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 定向领取的来源回收单库存明细（按件锁定）。
 * 一个领取任务可锁定多个来源回收单；匹配时写入 reservedQuantity（预占），
 * 签收按同一明细把预占转为已发放，取消/退回/换货/转家庭按同一明细释放未签收预占。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "aid_order_allocations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"distribution_id", "order_id"}))
public class AidOrderAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AidDistribution distribution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private RecycleOrder order;

    /** 当前仍被该任务锁定的件数（签收扣减、取消/退回释放） */
    @Column(nullable = false)
    private Integer reservedQuantity = 0;

    /** 该任务从此来源单实际签收的件数 */
    @Column(nullable = false)
    private Integer issuedQuantity = 0;

    private LocalDateTime createdAt = LocalDateTime.now();
}
