package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 投诉处理时间线：记录居民/回收员/分拣/社区/机构/财务各方在同一单中的处置动作 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "complaint_events")
public class ComplaintEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User author;

    /** 角色快照，如 分拣中心 */
    @Column(length = 20)
    private String partyRole;

    @Column(nullable = false, length = 1000)
    private String content;

    private LocalDateTime createdAt = LocalDateTime.now();
}
