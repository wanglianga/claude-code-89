package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 积分流水：回收获取、兑换消耗、财务补发（投诉补偿）、活动奖励 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "points_ledger")
public class PointsLedger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User resident;

    @ManyToOne(fetch = FetchType.LAZY)
    private RecycleOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointsType type;

    /** 带符号积分变动，如 +120 / -200 */
    private Integer points;

    /** 变动后余额 */
    private Integer balanceAfter;

    @Column(length = 255)
    private String remark;

    private LocalDateTime createdAt = LocalDateTime.now();
}
