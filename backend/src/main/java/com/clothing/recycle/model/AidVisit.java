package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 社区回访：穿着情况、满意度、后续需求；结果进入公益项目账本与小区复盘 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "aid_visits")
public class AidVisit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AidFamily family;

    @ManyToOne(fetch = FetchType.LAZY)
    private Batch batch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User visitedBy;

    /** 穿着情况 */
    @Column(length = 500)
    private String wearingSituation;

    /** 满意度 1-5 */
    private Integer satisfaction;

    /** 后续需求 */
    @Column(length = 500)
    private String followupNeed;

    @Column(length = 500)
    private String note;

    private LocalDateTime visitedAt = LocalDateTime.now();
}
