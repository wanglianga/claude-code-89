package com.clothing.recycle.model;

/** 投诉类型，覆盖六类争议 */
public enum ComplaintType {
    WEIGHT_DISPUTE,      // 质疑称重
    LATE_VISIT,          // 上门迟到
    MISJUDGED_DONATE,    // 衣物被误判不可捐赠
    ORG_REJECTED,        // 公益机构拒收
    POINTS_MISSING,      // 积分未到账
    OPAQUE_DESTINATION   // 公示去向不透明
}
