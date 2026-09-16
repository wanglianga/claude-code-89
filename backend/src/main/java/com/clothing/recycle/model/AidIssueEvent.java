package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 救助异常协同时间线：居民/社区/分拣/机构/回收员在同一回收单上下文中的处置记录 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "aid_issue_events")
public class AidIssueEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AidIssue issue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User author;

    @Column(length = 20)
    private String partyRole;

    @Column(nullable = false, length = 1000)
    private String content;

    private LocalDateTime createdAt = LocalDateTime.now();
}
