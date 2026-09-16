export const AID_PROOF = {
  LOW_INCOME: '低保证明',
  EXTREME_POOR: '特困证明',
  TEMP_RELIEF: '临时救助材料',
  COMMUNITY_AUTH: '社区授权'
}
export const AID_FAMILY_STATUS = {
  RESERVED: { label: '待匹配', type: 'warning' },
  MATCHED: { label: '已匹配待领取', type: 'primary' },
  RECEIVED: { label: '已领取', type: 'success' }
}
export const AID_DIST_STATUS = {
  MATCHED: { label: '待领取', type: 'warning' },
  HANDED_OUT: { label: '已领取', type: 'success' },
  EXCHANGED: { label: '换货完成', type: 'primary' },
  RETURNED: { label: '已退回重分', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}
export const AID_ISSUE_TYPE = {
  SIZE_WRONG: '尺码不合',
  NEED_MISMATCH: '衣物与需求不符',
  HYGIENE_DOUBT: '卫生被质疑',
  RECIPIENT_GIVEUP: '领取人临时放弃',
  SHORTAGE: '物资不足',
  FRAUD_DETECTED: '发现冒领'
}
