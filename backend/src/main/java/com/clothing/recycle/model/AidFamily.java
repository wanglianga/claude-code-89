package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 低收入家庭定向领取登记 */
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

    private Integer familySize;

    /** 困难情况说明（社区核实） */
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

    /** 登记/发放经办人 */
    @ManyToOne(fetch = FetchType.LAZY)
    private User operator;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime receivedAt;

    @Column(length = 500)
    private String remark;
}
