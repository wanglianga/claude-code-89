import { defineStore } from 'pinia'
import api from './api'

export const useAuth = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),
  getters: {
    isLogin: s => !!s.token,
    role: s => s.user?.role || '',
    roleLabel: s => s.user?.roleLabel || ''
  },
  actions: {
    async login(username, password) {
      const data = await api.post('/api/auth/login', { username, password })
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
      return data.user
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})

/** 各角色中文标签与状态配色，全应用复用 */
export const ROLE_LABELS = {
  RESIDENT: '居民', COLLECTOR: '回收员', SORTER: '分拣中心',
  COMMUNITY: '社区', ORG: '公益机构', FINANCE: '积分财务', ADMIN: '管理员'
}

export const ORDER_STATUS = {
  PENDING: { label: '待派单', type: 'info' },
  ASSIGNED: { label: '待上门', type: 'warning' },
  PICKED_UP: { label: '待分拣复核', type: 'primary' },
  SORTED: { label: '已分拣待入批', type: 'primary' },
  RETURNED: { label: '机构拒收待重分', type: 'danger' },
  IN_TRANSIT: { label: '运输在途', type: 'warning' },
  DONATED: { label: '已捐赠签收', type: 'success' },
  RECYCLED: { label: '已环保再生', type: 'success' },
  REJECTED: { label: '拒收/终止', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

export const SORT_CATEGORY = {
  DIRECT_DONATE: { label: '可直接捐赠', type: 'success' },
  NEED_CLEAN: { label: '需消毒整理', type: 'warning' },
  ECO_RECYCLE: { label: '环保再生', type: 'primary' },
  NON_RECYCLABLE: { label: '不可回收', type: 'danger' },
  SPECIAL: { label: '特殊处理', type: 'danger' }
}

export const COMPLAINT_TYPE = {
  WEIGHT_DISPUTE: '质疑称重',
  LATE_VISIT: '上门迟到',
  MISJUDGED_DONATE: '误判不可捐赠',
  ORG_REJECTED: '公益机构拒收',
  POINTS_MISSING: '积分未到账',
  OPAQUE_DESTINATION: '去向不透明'
}

export const COMPLAINT_STATUS = {
  OPEN: { label: '待受理', type: 'danger' },
  PROCESSING: { label: '处理中', type: 'warning' },
  RESOLVED: { label: '已解决', type: 'success' },
  CLOSED: { label: '已关闭', type: 'info' }
}

export const BATCH_STATUS = {
  STAGED: { label: '集货中', type: 'info' },
  IN_TRANSIT: { label: '在途', type: 'warning' },
  RECEIVED: { label: '机构已签收', type: 'success' },
  RECYCLED: { label: '再生完成', type: 'success' },
  REJECTED: { label: '机构拒收', type: 'danger' },
  AID_GIVEN: { label: '已定向发放', type: 'success' }
}

export const REJECT_REASON = {
  SIZE_MISMATCH: '尺码不匹配',
  SEASON_MISMATCH: '季节不匹配',
  HYGIENE: '卫生标准不匹配',
  OTHER: '其他原因'
}

export const RESORT_OUTCOME = {
  REDONATE: { label: '改配公益', type: 'success' },
  TO_RECYCLE: { label: '转环保再生', type: 'primary' },
  FINAL_REJECT: { label: '无害化处理', type: 'danger' }
}
