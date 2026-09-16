<template>
  <div>
    <h2 class="page-title">你好，{{ auth.user?.displayName }}</h2>
    <div class="page-sub">{{ today }} · {{ auth.roleLabel }}工作台 · {{ auth.user?.communityName || auth.user?.organizationName || '平台' }}</div>

    <!-- 概览指标（管理类角色） -->
    <el-row :gutter="14" v-if="overview" class="section-card">
      <el-col :xs="12" :sm="8" :md="4" v-for="c in overviewCards" :key="c.label">
        <div class="stat-card"><div class="num">{{ c.value }}</div><div class="label">{{ c.label }}</div></div>
      </el-col>
    </el-row>

    <!-- 居民积分卡 -->
    <el-card v-if="role==='RESIDENT'" class="section-card brand-gradient points-card">
      <div class="pc-left">
        <div class="pc-label">我的环保积分</div>
        <div class="pc-num">{{ auth.user?.pointsBalance ?? 0 }}</div>
      </div>
      <div class="pc-right">
        <el-button round @click="$router.push('/app/points')">积分兑换</el-button>
        <el-button round @click="$router.push('/app/orders')">我的回收单</el-button>
      </div>
    </el-card>

    <el-row :gutter="14">
      <el-col :xs="24" :md="12" v-for="panel in panels" :key="panel.title">
        <el-card class="section-card">
          <template #header>
            <div class="panel-head">
              <b>{{ panel.title }}（{{ panel.list.length }}）</b>
              <el-button link type="primary" @click="$router.push(panel.to)">前往处理 →</el-button>
            </div>
          </template>
          <el-table :data="panel.list.slice(0,5)" size="small" empty-text="暂无待办">
            <el-table-column prop="code" label="单号/批次" width="120">
              <template #default="{ row }">{{ row.code || ('#' + row.id) }}</template>
            </el-table-column>
            <el-table-column label="摘要">
              <template #default="{ row }">{{ row.summary }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.tagType">{{ row.tag }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../api'
import { useAuth, ORDER_STATUS, COMPLAINT_TYPE, COMPLAINT_STATUS, BATCH_STATUS } from '../store'

const auth = useAuth()
const role = computed(() => auth.role)
const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

const orders = ref([])
const complaints = ref([])
const batches = ref([])
const exchanges = ref([])
const overview = ref(null)

const overviewCards = computed(() => {
  if (!overview.value) return []
  const o = overview.value
  return [
    { label: '回收单总数', value: o.totalOrders },
    { label: '待派单', value: o.pendingCount },
    { label: '已捐赠签收', value: o.donatedCount },
    { label: '已环保再生', value: o.recycledCount },
    { label: '分拣重量(kg)', value: o.sortWeightKg },
    { label: '未结投诉', value: o.openComplaints }
  ]
})

function orderSummary(o) {
  return `${o.communityName} · ${o.itemCount}件 · ${o.address}`
}

const panels = computed(() => {
  const p = []
  if (role.value === 'RESIDENT') {
    p.push({ title: '进行中的回收单', to: '/app/orders', list: orders.value
      .filter(o => !['DONATED', 'RECYCLED', 'REJECTED', 'CANCELLED'].includes(o.status))
      .map(o => ({ ...o, summary: orderSummary(o), tag: ORDER_STATUS[o.status].label, tagType: ORDER_STATUS[o.status].type })) })
    p.push({ title: '我的投诉', to: '/app/complaints', list: complaints.value
      .filter(c => c.status !== 'RESOLVED' && c.status !== 'CLOSED')
      .map(c => ({ id: c.id, code: '#' + c.id, summary: `${c.order.code} · ${COMPLAINT_TYPE[c.type]}`, tag: COMPLAINT_STATUS[c.status].label, tagType: COMPLAINT_STATUS[c.status].type })) })
  }
  if (role.value === 'COLLECTOR') {
    p.push({ title: '待上门（已派给我）', to: '/app/orders', list: orders.value
      .filter(o => o.status === 'ASSIGNED')
      .map(o => ({ ...o, summary: `${o.timeSlot} · ${o.address}`, tag: ORDER_STATUS.ASSIGNED.label, tagType: 'warning' })) })
    p.push({ title: '待派单池（可抢单）', to: '/app/orders', list: orders.value
      .filter(o => o.status === 'PENDING')
      .map(o => ({ ...o, summary: orderSummary(o), tag: '待派单', tagType: 'info' })) })
  }
  if (role.value === 'SORTER') {
    p.push({ title: '待分拣复核', to: '/app/orders', list: orders.value
      .filter(o => o.status === 'PICKED_UP')
      .map(o => ({ ...o, summary: `上门称重 ${o.pickupWeight}kg · ${o.communityName}`, tag: '待复核', tagType: 'primary' })) })
    p.push({ title: '批次工作', to: '/app/batches', list: [
      ...batches.value.filter(b => b.status === 'STAGED').map(b => ({ ...b, summary: `集货中 ${b.totalWeightKg}kg`, tag: BATCH_STATUS.STAGED.label, tagType: 'info' })),
      ...batches.value.filter(b => b.status === 'IN_TRANSIT' && b.batchType === 'RECYCLE').map(b => ({ ...b, summary: '再生批次待登记处理量', tag: BATCH_STATUS.IN_TRANSIT.label, tagType: 'warning' }))
    ] })
  }
  if (role.value === 'COMMUNITY' || role.value === 'ADMIN') {
    p.push({ title: '待派单', to: '/app/orders', list: orders.value.filter(o => o.status === 'PENDING')
      .map(o => ({ ...o, summary: orderSummary(o), tag: '待派单', tagType: 'info' })) })
    p.push({ title: '待处理投诉', to: '/app/complaints', list: complaints.value
      .filter(c => c.status !== 'RESOLVED' && c.status !== 'CLOSED')
      .map(c => ({ id: c.id, code: '#' + c.id, summary: `${c.order.code} · ${COMPLAINT_TYPE[c.type]} · ${c.resident.displayName}`, tag: COMPLAINT_STATUS[c.status].label, tagType: COMPLAINT_STATUS[c.status].type })) })
  }
  if (role.value === 'ORG') {
    p.push({ title: '待签收/拒收批次', to: '/app/batches', list: batches.value.filter(b => b.status === 'IN_TRANSIT')
      .map(b => ({ ...b, summary: `${b.projectName || b.recyclerName} · ${b.totalWeightKg}kg`, tag: '待签收', tagType: 'warning' })) })
    p.push({ title: '已签收批次', to: '/app/batches', list: batches.value.filter(b => ['RECEIVED', 'AID_GIVEN'].includes(b.status))
      .map(b => ({ ...b, summary: `${b.projectName} · 签收人 ${b.receiverName || '-'}`, tag: BATCH_STATUS[b.status].label, tagType: 'success' })) })
  }
  if (role.value === 'FINANCE') {
    p.push({ title: '待发放兑换单', to: '/app/points', list: exchanges.value.filter(e => e.status === 'ORDERED')
      .map(e => ({ id: e.id, code: 'EX#' + e.id, summary: `${e.residentName} · ${e.product.name} ×${e.quantity}`, tag: '待发放', tagType: 'warning' })) })
    p.push({ title: '积分相关投诉', to: '/app/complaints', list: complaints.value
      .filter(c => (c.type === 'POINTS_MISSING' || c.type === 'WEIGHT_DISPUTE') && c.status !== 'RESOLVED')
      .map(c => ({ id: c.id, code: '#' + c.id, summary: `${c.order.code} · ${COMPLAINT_TYPE[c.type]}`, tag: COMPLAINT_STATUS[c.status].label, tagType: COMPLAINT_STATUS[c.status].type })) })
  }
  if (role.value === 'SORTER' && p.length < 2) {
    p.push({ title: '已分拣待入批', to: '/app/batches', list: orders.value.filter(o => o.status === 'SORTED')
      .map(o => ({ ...o, summary: `${o.sort.category} · ${o.sort.weightKg}kg`, tag: '待入批', tagType: 'primary' })) })
  }
  if (p.length === 0) p.push({ title: '提示', to: '/app/orders', list: [], })
  return p.slice(0, 4)
})

onMounted(async () => {
  const roleMe = auth.role
  const [o, c] = await Promise.all([api.get('/api/orders'), api.get('/api/complaints')])
  orders.value = o
  complaints.value = c
  if (['SORTER', 'COMMUNITY', 'ORG', 'ADMIN'].includes(roleMe)) {
    batches.value = await api.get('/api/batches')
  }
  if (['FINANCE', 'ADMIN'].includes(roleMe)) {
    exchanges.value = await api.get('/api/points/exchanges')
  }
  if (['COMMUNITY', 'FINANCE', 'ADMIN'].includes(roleMe)) {
    try { overview.value = await api.get('/api/stats/overview') } catch (e) { overview.value = null }
  }
})
</script>

<style scoped>
.panel-head { display: flex; justify-content: space-between; align-items: center; }
.points-card { display: flex; justify-content: space-between; align-items: center; border: none; }
.pc-num { font-size: 40px; font-weight: 800; }
.pc-label { opacity: .9; }
.pc-right { display: flex; gap: 10px; }
:deep(.el-card.points-card) { background: linear-gradient(120deg, #1f6f54, #47a084); }
</style>
