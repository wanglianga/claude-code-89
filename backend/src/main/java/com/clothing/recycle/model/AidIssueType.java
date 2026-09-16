package com.clothing.recycle.model;

/** 定向领取异常类型（居民/社区/分拣/机构/回收员在同一回收单中协同处理） */
public enum AidIssueType {
    SIZE_WRONG,       // 尺码不合
    NEED_MISMATCH,    // 衣物与需求不符
    HYGIENE_DOUBT,    // 卫生被质疑
    RECIPIENT_GIVEUP, // 领取人临时放弃
    SHORTAGE,         // 物资不足
    FRAUD_DETECTED    // 社区发现冒领
}
