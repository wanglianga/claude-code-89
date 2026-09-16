package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 分拣中心复核：五分类 + 重量差异 + 污损原因 + 去向 + 隐私衣物处置 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sort_reviews")
public class SortReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private RecycleOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User sorter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SortCategory category;

    /** 分拣复核重量（kg） */
    @Column(precision = 8, scale = 2)
    private BigDecimal weightKg;

    /** 与上门称重的差异（分拣重量-上门重量，kg） */
    @Column(precision = 8, scale = 2)
    private BigDecimal weightDiff;

    /** 污损/判定原因 */
    @Column(length = 500)
    private String damageReason;

    /** 去向说明：如"发往暖阳公益-打工子弟冬季外套项目" */
    @Column(length = 500)
    private String destination;

    /** 是否含校服/工作服/个人信息衣物（隐私风险） */
    private boolean privacyRisk;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private PrivacyAction privacyAction = PrivacyAction.NONE;

    /** 脱敏说明：拆除校徽工牌/涂销姓名等 */
    @Column(length = 500)
    private String privacyNote;

    private String photoPath;

    private LocalDateTime reviewedAt = LocalDateTime.now();
}
