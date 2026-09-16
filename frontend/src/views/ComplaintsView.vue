<template>
  <div>
    <h2 class="page-title">投诉协同处理</h2>
    <div class="page-sub">质疑称重、上门迟到、误判不可捐赠、机构拒收、积分未到账、去向不透明——六类争议在同一回收单上下文中跨角色处理</div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="9">
        <el-card>
          <div class="toolbar">
            <el-select v-model="typeFilter" placeholder="全部类型" clearable size="small" style="width:140px" @change="load">
              <el-option v-for="(label,k) in COMPLAINT_TYPE" :key="k" :label="label" :value="k" />
            </el-select>
            <el-select v-model="statusFilter" placeholder="全部状态" clearable size="small" style="width:120px" @change="load">
              <el-option v-for="(v,k) in COMPLAINT_STATUS" :key="k" :label="v.label" :value="k" />
            </el-select>
            <el-button size="small" :icon="Refresh" @click="load" />
          </div>
          <el-scrollbar height="calc(100vh - 250px)">
            <div v-for="c in filtered" :key="c.id" class="c-item"
                 :class="{active: current?.id===c.id}" @click="open(c)">
              <div class="ci-top">
                <el-tag size="small" type="warning">{{ c.typeLabel }}</el-tag>
                <el-tag size="small" :type="COMPLAINT_STATUS[c.status].type">{{ COMPLAINT_STATUS[c.status].label }}</el-tag>
              </div>
              <div class="ci-desc">{{ c.description }}</div>
              <div class="ci-meta">
                {{ c.order.code }} · {{ c.resident.displayName }} · {{ fmt(c.createdAt) }}
              </div>
              <div class="ci-handler">牵头：{{ c.handler ? c.handler.roleLabel + '·' + c.handler.displayName : '待分派' }}</div>
            </div>
            <el-empty v-if="!filtered.length" :image-size="70" description="暂无投诉" />
          </el-scrollbar>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="15">
        <el-card v-if="current">
          <template #header>
            <div class="d-head">
              <div>
                <b style="font-size:16px">投诉 #{{ current.id }} · {{ current.typeLabel }}</b>
                <el-tag size="small" :type="COMPLAINT_STATUS[current.status].type" style="margin-left:8px">
                  {{ COMPLAINT_STATUS[current.status].label }}
                </el-tag>
              </div>
              <el-button size="small" @click="openOrder">查看回收单全流程</el-button>
            </div>
          </template>

          <el-alert :title="`回收单 ${current.order.code} · ${current.order.communityName} · ${current.order.address}`"
                    type="info" :closable="false" style="margin-bottom:14px" />
          <div class="resident-say">
            <el-avatar :size="30" class="ava">{{ current.resident.displayName[0] }}</el-avatar>
            <div class="bubble">
              <div class="b-meta">{{ current.resident.displayName }}（居民）· {{ fmt(current.createdAt) }}</div>
              {{ current.description }}
            </div>
          </div>

          <el-divider content-position="left">协同处理时间线（{{ current.events.length }}）</el-divider>
          <el-timeline>
            <el-timeline-item v-for="e in current.events" :key="e.id"
                              :timestamp="fmt(e.createdAt)" placement="top"
                              :type="roleColor(e.partyRole)">
              <el-card shadow="never" class="evt">
                <div class="evt-head">
                  <el-tag size="small" :type="roleColor(e.partyRole)">{{ e.partyRole }}</el-tag>
                  <b>{{ e.authorName }}</b>
                </div>
                <div class="evt-body">{{ e.content }}</div>
              </el-card>
            </el-timeline-item>
          </el-timeline>

          <div v-if="current.status==='RESOLVED'" class="resolved-box">
            ✅ 处理结果：{{ current.resolutionNote }}
          </div>

          <template v-if="current.status!=='RESOLVED' && role!=='RESIDENT'">
            <el-divider content-position="left">补充处理进展</el-divider>
            <el-input v-model="newEvent" type="textarea" :rows="2"
                      :placeholder="`以「${auth.roleLabel}」身份补充说明/处置动作，所有角色与居民可见`" />
            <div class="resolve-row">
              <el-button type="primary" plain @click="addEvent">发布进展</el-button>
              <div style="flex:1"></div>
              <el-popconfirm title="确认结案？" @confirm="resolve(false)">
                <template #reference>
                  <el-button type="success">仅结案</el-button>
                </template>
              </el-popconfirm>
              <el-button v-if="['FINANCE','ADMIN'].includes(role)" type="warning" @click="compDlg=true">
                补发积分并结案
              </el-button>
            </div>
          </template>
          <el-alert v-if="current.status!=='RESOLVED' && role==='RESIDENT'" type="info" :closable="false"
                    title="投诉处理中，各角色回复将实时展示在此时间线上；结案后可查看处理结果。" />
        </el-card>
        <el-empty v-else description="← 选择一条投诉查看协同处理全过程" style="margin-top:120px" />
      </el-col>
    </el-row>

    <!-- 财务补发 -->
    <el-dialog v-model="compDlg" title="积分财务：补发积分并结案" width="440px">
      <el-form label-width="100px">
        <el-form-item label="补发积分"><el-input-number v-model="compPoints" :min="1" :step="50" /></el-form-item>
        <el-form-item label="处理说明">
          <el-input v-model="resolution" type="textarea" :rows="3" placeholder="说明核实结论与补发依据" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="compDlg=false">取消</el-button>
        <el-button type="warning" @click="resolve(true)">确认补发并结案</el-button>
      </template>
    </el-dialog>

    <!-- 回收单全流程 -->
    <el-drawer v-model="orderDlg" size="480px" :title="`回收单 ${order?.code || ''}`">
      <template v-if="order">
        <el-steps direction="vertical" :active="stepActive" finish-status="success">
          <el-step title="居民预约" :description="`${order.itemCount}件 · ${order.categories} · ${order.timeSlot}`" />
          <el-step title="派单/上门" :description="order.pickup ? `${order.collector?.displayName} · ${order.pickup.weightKg}kg${order.pickup.arrivedLate?' · 迟到':''}` : '等待回收员上门'" />
          <el-step title="居民确认" :description="order.pickup?.residentConfirmed ? {POINTS:'积分入账',DONATION:'公益捐赠',MIXED:'积分+捐赠'}[order.pickup.confirmOption] : '待居民确认'" />
          <el-step title="分拣复核" :description="order.sort ? `${SORT_CATEGORY[order.sort.category].label} · 差异${order.sort.weightDiff}kg` : '待复核'" />
          <el-step title="批次去向" :description="order.batch ? `${order.batch.code} · ${BATCH_STATUS[order.batch.status].label}` : '待集货入批'" />
        </el-steps>
        <el-image v-if="order.pickup?.photoUrl" :src="order.pickup.photoUrl" class="photo-thumb" style="max-height:200px;margin-top:12px" />
        <el-alert v-if="order.privacy" type="success" :closable="false" class="privacy-alert"
                  :title="`隐私处置结论：${order.privacy.label}`"
                  :description="order.privacy.conclusion" />
        <el-image v-if="order.privacy?.evidencePhotoUrl" :src="order.privacy.evidencePhotoUrl"
                  class="photo-thumb" style="max-height:180px;margin-top:8px"
                  :preview-src-list="[order.privacy.evidencePhotoUrl]" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import api from '../api'
import { useAuth, COMPLAINT_TYPE, COMPLAINT_STATUS, SORT_CATEGORY, BATCH_STATUS } from '../store'

const auth = useAuth()
const role = computed(() => auth.role)

const list = ref([])
const current = ref(null)
const typeFilter = ref('')
const statusFilter = ref('')
const newEvent = ref('')
const compDlg = ref(false)
const compPoints = ref(50)
const resolution = ref('')
const orderDlg = ref(false)
const order = ref(null)

const filtered = computed(() => list.value.filter(c =>
  (!typeFilter.value || c.type === typeFilter.value) &&
  (!statusFilter.value || c.status === statusFilter.value)))

async function load() {
  const p = new URLSearchParams()
  if (typeFilter.value) p.set('type', typeFilter.value)
  if (statusFilter.value) p.set('status', statusFilter.value)
  list.value = await api.get('/api/complaints' + (p.toString() ? '?' + p : ''))
  if (current.value) {
    const fresh = list.value.find(c => c.id === current.value.id)
    if (fresh) current.value = fresh
  }
}
onMounted(load)

async function open(c) {
  current.value = await api.get(`/api/complaints/${c.id}`)
}

async function addEvent() {
  if (!newEvent.value.trim()) return ElMessage.warning('请输入处理进展')
  await api.post(`/api/complaints/${current.value.id}/events`, { content: newEvent.value })
  newEvent.value = ''
  ElMessage.success('已发布到协同时间线')
  await open(current.value)
  load()
}

async function resolve(withComp) {
  if (!resolution.value.trim() && !withComp) resolution.value = '问题已核实处理完成'
  if (withComp && !resolution.value.trim()) return ElMessage.warning('请填写补发依据')
  await api.post(`/api/complaints/${current.value.id}/resolve`, {
    resolutionNote: resolution.value,
    compensatePoints: withComp ? compPoints.value : null
  })
  ElMessage.success(withComp ? '已补发积分并结案' : '投诉已结案')
  compDlg.value = false
  resolution.value = ''
  await open(current.value)
  load()
}

const stepActive = computed(() => {
  if (!order.value) return 0
  const s = order.value.status
  return { PENDING: 1, ASSIGNED: 2, PICKED_UP: order.value.pickup?.residentConfirmed ? 3 : 2,
    SORTED: 4, IN_TRANSIT: 5, DONATED: 5, RECYCLED: 5, REJECTED: 4 }[s] ?? 1
})
async function openOrder() {
  order.value = await api.get(`/api/orders/${current.value.order.id}`)
  orderDlg.value = true
}

function roleColor(r) {
  return { 居民: 'success', 回收员: 'warning', 分拣中心: 'primary',
    社区: 'info', 公益机构: 'warning', 积分财务: 'danger', 管理员: 'info' }[r] || 'info'
}
function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' }
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 10px; }
.c-item { border: 1px solid #edf0f2; border-radius: 10px; padding: 10px 12px; margin-bottom: 8px; cursor: pointer; transition: .15s; }
.c-item:hover { border-color: var(--brand); background: #f6fbf9; }
.c-item.active { border-color: var(--brand); background: var(--brand-light); }
.ci-top { display: flex; justify-content: space-between; }
.ci-desc { font-size: 13px; margin: 6px 0; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.ci-meta, .ci-handler { font-size: 11px; color: #97a0af; }
.d-head { display: flex; justify-content: space-between; align-items: center; }
.resident-say { display: flex; gap: 10px; }
.ava { background: var(--brand); color: #fff; }
.bubble { background: var(--brand-light); border-radius: 10px; padding: 10px 12px; font-size: 13px; flex: 1; }
.b-meta { font-size: 11px; color: #7a869a; margin-bottom: 4px; }
.evt :deep(.el-card__body) { padding: 10px 12px; }
.evt-head { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; }
.evt-body { font-size: 13px; line-height: 1.6; }
.resolve-row { display: flex; gap: 10px; margin-top: 12px; align-items: center; }
.resolved-box { background: #f0f9eb; border: 1px solid #c2e7b0; border-radius: 8px; padding: 10px 14px; font-size: 13px; color: #4e8a2f; }
.privacy-alert { margin-top: 10px; }
.privacy-alert :deep(.el-alert__description) { font-size: 12px; }
</style>
