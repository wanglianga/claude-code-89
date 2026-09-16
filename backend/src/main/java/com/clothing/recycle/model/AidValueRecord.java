package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 公益价值记账：同一发放批次只记一次公益价值，
 *  避免积分、捐赠金额与物资价值重复入账（即使衣物来源单居民选择了积分）。 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "aid_value_records",
        uniqueConstraints = @UniqueConstraint(columnNames = "batch_id"))
public class AidValueRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", unique = true)
    private Batch batch;

    /** 物资公益价值（元，按批次一次性估算） */
    @Column(precision = 10, scale = 2)
    private BigDecimal valueAmount;

    /** 发放件数快照 */
    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User recordedBy;

    @Column(length = 500)
    private String remark;

    private LocalDateTime createdAt = LocalDateTime.now();
}
