package com.clothing.recycle.model;

public enum BatchStatus {
    STAGED,       // 分拣中心集货中
    IN_TRANSIT,   // 已发运，待公益机构签收
    RECEIVED,     // 公益机构已签收
    RECYCLED,     // 再生处理完成
    REJECTED,     // 公益机构拒收
    AID_GIVEN     // 已定向发放给低收入家庭
}
