package com.clothing.recycle.model;

/** 隐私风险衣物处置方式（校服/工作服/个人信息） */
public enum PrivacyAction {
    NONE,           // 无隐私风险
    DESENSITIZED,   // 已脱敏（拆除校徽工牌、涂销信息）后流转
    REJECTED        // 拒收，不进入公益流转
}
