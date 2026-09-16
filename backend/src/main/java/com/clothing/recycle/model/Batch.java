package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 公示批次：同一去向（公益项目/再生处理厂）的回收单集货发运，支撑签收与公示 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "batches")
public class Batch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 32)
    private String code;

    /** 批次主要分类：可直接捐赠/需消毒整理/环保再生 */
    @Column(length = 32)
    private String batchType;

    /** 去向公益机构（再生批次可为空） */
    @ManyToOne(fetch = FetchType.LAZY)
    private User organization;

    /** 公益项目名称 */
    @Column(length = 120)
    private String projectName;

    /** 再生处理厂名称（环保再生批次） */
    @Column(length = 120)
    private String recyclerName;

    /** 关联学校/企业公益活动 */
    @ManyToOne(fetch = FetchType.LAZY)
    private Partner partner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BatchStatus status = BatchStatus.STAGED;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalWeightKg = BigDecimal.ZERO;

    private Integer itemCount = 0;

    /** 捐赠照片（分拣打包/装车/发放） */
    private String donationPhotoPath;

    /** 公益机构签收照片 */
    private String signPhotoPath;

    @Column(length = 200)
    private String receiverName;

    @Column(length = 500)
    private String signNote;

    /** 拒收原因（公益机构拒收时） */
    @Column(length = 500)
    private String rejectReason;

    /** 再生处理量（kg） */
    @Column(precision = 10, scale = 2)
    private BigDecimal recycledWeightKg;

    private LocalDateTime shippedAt;
    private LocalDateTime signedAt;
    private LocalDateTime recycledAt;
    private LocalDateTime aidGivenAt;

    /** 公示说明，面向居民公开 */
    @Column(length = 1000)
    private String publicNote;

    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "batch")
    private List<RecycleOrder> orders = new ArrayList<>();
}
