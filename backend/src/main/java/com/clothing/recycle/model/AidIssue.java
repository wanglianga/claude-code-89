package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 领取异常：尺码不合/需求不符/卫生质疑/临时放弃/物资不足/冒领。
 *  挂在具体回收单上下文中由各方协同处理，处置不得直接改写公示去向。 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "aid_issues")
public class AidIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AidDistribution distribution;

    /** 同一回收单上下文 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private RecycleOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AidIssueType type;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ComplaintStatus status = ComplaintStatus.OPEN;

    /** 处置方式：换货 / 退回重新分拣 / 转其他登记家庭 / 补充分拣 / 取消 */
    @Column(length = 24)
    private String resolutionAction;

    @Column(length = 1000)
    private String resolutionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    private User resolvedBy;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime resolvedAt;
}
