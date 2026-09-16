package com.clothing.recycle.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 投诉/争议单：六类争议在同一回收单上下文里跨角色协同处理 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "complaints")
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private RecycleOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ComplaintType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ComplaintStatus status = ComplaintStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User resident;

    @Column(nullable = false, length = 1000)
    private String description;

    /** 当前牵头处理人（回收员/分拣主管/社区/财务/机构联络人） */
    @ManyToOne(fetch = FetchType.LAZY)
    private User handler;

    @Column(length = 1000)
    private String resolutionNote;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime resolvedAt;

    @OneToMany(mappedBy = "complaint", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ComplaintEvent> events = new ArrayList<>();
}
