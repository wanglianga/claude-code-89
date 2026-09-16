package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 定向领取任务：分拣中心匹配生成 → 通知社区经办人 → 领取时核验身份/代领授权并签收 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "aid_distributions")
public class AidDistribution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AidFamily family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Batch batch;

    /** 匹配分拣员 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User matchedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AidDistributionStatus status = AidDistributionStatus.MATCHED;

    /** 计划发放件数 */
    private Integer plannedQuantity;

    /** 匹配的回收单 id（逗号分隔，须全部为可直接捐赠类，用于卫生与去向追溯） */
    @Column(length = 300)
    private String matchedOrderIds;

    /** 实际领取件数 */
    private Integer actualQuantity;

    /** 领取人与登记人关系：本人/配偶/子女/亲属/社区工作人员代领 */
    @Column(length = 40)
    private String receiverRelation;

    /** 代领人脱敏姓名 */
    @Column(length = 30)
    private String proxyName;

    /** 代领授权：登记人电话确认 / 社区确认授权书编号 */
    @Column(length = 300)
    private String proxyAuthNote;

    /** 签收照片或签字留痕 */
    private String signPhotoPath;

    /** 领取核验经办人（社区） */
    @ManyToOne(fetch = FetchType.LAZY)
    private User handedBy;

    private LocalDateTime handedAt;

    @Column(length = 500)
    private String handNote;

    /** 是否经异常换货后完成 */
    private boolean rehandled;

    private LocalDateTime createdAt = LocalDateTime.now();
}
