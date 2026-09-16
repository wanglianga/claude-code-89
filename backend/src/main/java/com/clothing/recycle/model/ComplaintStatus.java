package com.clothing.recycle.model;

public enum ComplaintStatus {
    OPEN,          // 已受理
    PROCESSING,    // 跨角色处理中
    RESOLVED,      // 已解决
    CLOSED         // 已关闭（居民认可/驳回）
}
