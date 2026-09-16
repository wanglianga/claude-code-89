package com.clothing.recycle.model;

/** 回收单状态：串联 居民→回收员→分拣中心→公益/再生 的全生命周期 */
public enum OrderStatus {
    PENDING,      // 已预约，待派单
    ASSIGNED,     // 已派单，等待上门
    PICKED_UP,    // 已上门回收，待分拣复核
    SORTED,       // 分拣复核完成，待入批
    IN_TRANSIT,   // 已随批次发运
    DONATED,      // 已公益捐赠签收
    RECYCLED,     // 已环保再生处理
    REJECTED,     // 拒收/不可回收/特殊处理
    CANCELLED     // 居民取消
}
