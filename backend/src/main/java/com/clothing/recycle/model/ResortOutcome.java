package com.clothing.recycle.model;

/** 拒收退回后，分拣中心重新分拣的总体结论 */
public enum ResortOutcome {
    REDONATE,     // 重新整理后改配其他公益机构/项目
    TO_RECYCLE,   // 不符合捐赠标准，转环保再生
    FINAL_REJECT  // 仍不可用，无害化处理
}
