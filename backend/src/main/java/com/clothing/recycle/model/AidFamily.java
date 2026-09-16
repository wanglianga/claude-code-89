package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 低收入/特困/临时救助家庭需求登记（敏感信息仅脱敏存储，公示端不展示） */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "aid_families")
public class AidFamily {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 脱敏姓名，如 张* */
    @Column(nullable = false, length = 30)
    private String maskedName;

    /** 脱敏电话，如 138****2103 */
    @Column(length = 20)
    private String maskedPhone;

    @Column(nullable = false, length = 100)
    private String communityName;

    /** 所在街道，用于本街道优先调剂 */
    @Column(length = 100)
    private String subdistrict;

    /** 资格证明类型 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AidProofType proofType;

    /** 核验材料备注（仅记录脱敏摘要与核验结论，不留证件影像） */
    @Column(length = 500)
    private String proofNote;

    /** 社区核验经办人 */
    @ManyToOne(fetch = FetchType.LAZY)
    private User verifiedBy;

    private LocalDateTime verifiedAt;

    private Integer familySize;

    /** 需求人数 */
    private Integer needCount;

    /** 性别/年龄构成，如 女35岁、男8岁 */
    @Column(length = 200)
    private String genderAgeDesc;

    /** 所需尺码，逗号分隔，如 130,S,M */
    @Column(length = 200)
    private String sizes;

    /** 适用季节，如 秋冬 */
    @Column(length = 100)
    private String seasons;

    /** 衣物类型需求，逗号分隔 */
    @Column(length = 200)
    private String clothTypes;

    /** 是否接受二手衣物 */
    private boolean acceptUsed = true;

    /** 是否需要鞋包被褥 */
    private boolean needShoesBagsBedding;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private DeliveryMethod deliveryMethod = DeliveryMethod.PICKUP;

    /** 是否同意在公示端隐藏个人明细（默认隐藏） */
    private boolean publicHidden = true;

    /** 企业/学校指定捐赠对象名称（无则为空，按公开匹配规则处理） */
    @Column(length = 120)
    private String designatedTarget;

    /** 困难情况说明（社区核实，不对外公示） */
    @Column(length = 500)
    private String needNote;

    /** 领取凭证号 */
    @Column(length = 32)
    private String voucherNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AidStatus status = AidStatus.RESERVED;

    /** 领取衣物来自的捐赠批次 */
    @ManyToOne(fetch = FetchType.LAZY)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    private User operator;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime receivedAt;

    @Column(length = 500)
    private String remark;
}
