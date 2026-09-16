package com.clothing.recycle.model;

/** 定向领取任务状态 */
public enum AidDistributionStatus {
    MATCHED,    // 已匹配，待领取
    HANDED_OUT, // 已领取完成
    EXCHANGED,  // 发生换货（异常处理后完成）
    RETURNED,   // 退回重新分拣
    CANCELLED   // 放弃/取消
}
