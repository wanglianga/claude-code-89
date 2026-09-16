<template>
  <div>
    <h2 class="page-title">回收单</h2>
    <div class="page-sub">一张回收单串联居民、回收员、分拣中心、社区、公益机构、积分财务六方留痕</div>

    <el-card class="section-card">
      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable size="default" style="width:170px" @change="load">
          <el-option v-for="(v,k) in ORDER_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-input v-model="kw" placeholder="搜索单号/地址/小区" clearable style="width:240px" />
        <div style="flex:1"></div>
        <el-button v-if="role==='RESIDENT'" type="primary" :icon="Plus" @click="openCreate">提交旧衣回收预约</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-table :data="filtered" size="small" stripe>
        <el-table-column label="单号" width="120">
          <template #default="{row}"><b>{{ row.code }}</b></template>
        </el-table-column>
        <el-table-column v-if="role!=='RESIDENT'" label="居民" width="100">
          <template #default="{row}">{{ row.resident.displayName }}</template>
        </el-table-column>
        <el-table-column prop="communityName" label="小区" width="120" />
        <el-table-column label="预约内容" min-width="200">
          <template #default="{row}">
            <div>{{ row.itemCount }} 件 · {{ row.categories }}
              <el-tag v-if="row.washed" size="small" type="success" effect="plain">已清洗</el-tag>
              <el-tag v-if="row.hasShoesBagsBedding" size="small" effect="plain">鞋包被褥</el-tag>
              <el-tag v-if="row.partner" size="small" type="warning" effect="plain">
                {{ row.partner.type==='SCHOOL' ? '学校活动' : '企业合作' }}
              </el-tag>
            </div>
            <div class="muted">{{ row.address }} · {{ row.timeSlot }}</div>
          </template>
        </el-table-column>
        <el-table-column label="重量" width="110">
          <template #default="{row}">
            <div v-if="row.pickupWeight">上门 {{ row.pickupWeight }}kg</div>
            <div v-if="row.sort" :style="{color: Math.abs(Number(row.sort.weightDiff))>0.5 ? '#e6a23c' : ''}">
              分拣 {{ row.sort.weightKg }}kg
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="118">
          <template #default="{row}">
            <el-tag size="small" :type="ORDER_STATUS[row.status].type">{{ ORDER_STATUS[row.status].label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="role==='RESIDENT'" link type="warning" size="small" @click="openComplaint(row)">投诉</el-button>
            <el-button v-if="role==='COLLECTOR' && row.status==='PENDING'" link type="success" size="small" @click="assign(row)">抢单</el-button>
            <el-button v-if="['COMMUNITY','ADMIN'].includes(role) && row.status==='PENDING'" link type="success" size="small" @click="openAssign(row)">派单</el-button>
            <el-button v-if="role==='COLLECTOR' && row.status==='ASSIGNED' && row.collector?.username===auth.user.username"
                       link type="primary" size="small" @click="openPickup(row)">上门登记</el-button>
            <el-button v-if="role==='RESIDENT' && row.status==='PICKED_UP' && row.pickup && !row.pickup.residentConfirmed"
                       link type="success" size="small" @click="openConfirm(row)">确认积分/捐赠</el-button>
            <el-button v-if="role==='SORTER' && row.status==='PICKED_UP'" link type="primary" size="small" @click="openSort(row)">分拣复核</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建预约 -->
    <el-dialog v-model="createDlg" title="提交旧衣回收预约" width="560px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="衣物数量(件)"><el-input-number v-model="form.itemCount" :min="1" /></el-form-item>
        <el-form-item label="品类">
          <el-checkbox-group v-model="form.catArr">
            <el-checkbox v-for="c in CAT_OPTIONS" :key="c" :label="c" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="清洗情况"><el-switch v-model="form.washed" active-text="已清洗" inactive-text="未清洗" /></el-form-item>
        <el-form-item label="鞋包被褥"><el-switch v-model="form.hasShoesBagsBedding" active-text="含鞋/包/被褥" /></el-form-item>
        <el-form-item label="上门地址"><el-input v-model="form.address" placeholder="小区+楼栋门牌" /></el-form-item>
        <el-form-item label="可预约时段">
          <el-select v-model="form.timeSlot" placeholder="选择时段" style="width:100%">
            <el-option v-for="s in SLOTS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="希望捐赠"><el-switch v-model="form.donateWanted" active-text="希望公益捐赠" inactive-text="倾向积分" /></el-form-item>
        <el-form-item label="公益活动">
          <el-select v-model="form.partnerId" clearable placeholder="不参加活动（可选）" style="width:100%">
            <el-option v-for="p in partners" :key="p.id" :label="`${p.type==='SCHOOL'?'学校':'企业'} · ${p.name} · ${p.projectName}`" :value="p.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDlg=false">取消</el-button>
        <el-button type="primary" @click="submitCreate">提交预约</el-button>
      </template>
    </el-dialog>

    <!-- 上门登记 -->
    <el-dialog v-model="pickupDlg" title="回收员上门登记" width="520px">
      <el-form :model="pickupForm" label-width="100px">
        <el-form-item label="现场称重kg"><el-input-number v-model="pickupForm.weightKg" :min="0.1" :precision="2" :step="0.5" /></el-form-item>
        <el-form-item label="初步分类">
          <el-select v-model="pickupForm.preCategories" style="width:100%">
            <el-option v-for="c in PRE_CATS" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否迟到"><el-switch v-model="pickupForm.arrivedLate" active-text="本次上门迟到" /></el-form-item>
        <el-form-item label="现场照片"><el-upload :http-request="uploadPickupPhoto" :show-file-list="false" accept="image/*">
          <el-button :icon="Picture">上传称重/打包照片（必传）</el-button>
          <el-image v-if="pickupForm.photoUrl" :src="pickupForm.photoUrl" style="width:180px;margin-left:12px" />
        </el-upload>
        <div class="muted" v-if="!pickupForm.photoUrl">无现场称重照片不能完成上门登记</div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="pickupForm.note" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pickupDlg=false">取消</el-button>
        <el-button type="primary" @click="submitPickup">提交并等待居民确认</el-button>
      </template>
    </el-dialog>

    <!-- 居民确认 -->
    <el-dialog v-model="confirmDlg" title="居民确认：积分 或 公益捐赠" width="480px">
      <el-alert type="info" :closable="false" show-icon
                :title="`上门称重 ${current?.pickup?.weightKg}kg，照片已上传，初步分类：${current?.pickup?.preCategories}`" />
      <el-radio-group v-model="confirmOption" class="confirm-opts">
        <el-radio value="POINTS" border>🪙 全部换积分（50 分/kg，预计 {{ estPoints('POINTS') }} 分）</el-radio>
        <el-radio value="DONATION" border>❤️ 全部公益捐赠{{ current?.partner ? '（活动奖励 100 分）' : '' }}</el-radio>
        <el-radio value="MIXED" border>🤝 积分+捐赠（25 分/kg，预计 {{ estPoints('MIXED') }} 分）</el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="confirmDlg=false">取消</el-button>
        <el-button type="primary" @click="submitConfirm">签字确认</el-button>
      </template>
    </el-dialog>

    <!-- 分拣复核 -->
    <el-dialog v-model="sortDlg" title="分拣中心复核" width="600px">
      <el-form :model="sortForm" label-width="110px">
        <el-form-item label="复核分类">
          <el-radio-group v-model="sortForm.category">
            <el-radio-button v-for="(v,k) in SORT_CATEGORY" :key="k" :value="k">{{ v.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复核重量kg">
          <el-input-number v-model="sortForm.weightKg" :min="0.1" :precision="2" :step="0.1" />
          <span class="muted" v-if="current">（上门 {{ current.pickupWeight }}kg）</span>
        </el-form-item>
        <el-form-item label="污损原因"><el-input v-model="sortForm.damageReason" type="textarea"
          placeholder="如：霉变/破洞/异味/材质不可再生；可直接捐赠可留空" /></el-form-item>
        <el-form-item label="去向"><el-input v-model="sortForm.destination" placeholder="如：暖阳公益-冬季温暖包 / 绿纤再生厂" /></el-form-item>
        <el-form-item label="隐私风险衣物">
          <el-switch v-model="sortForm.privacyRisk" active-text="含校服/工作服/个人信息" />
        </el-form-item>
        <template v-if="sortForm.privacyRisk">
          <el-form-item label="隐私处置">
            <el-radio-group v-model="sortForm.privacyAction">
              <el-radio value="DESENSITIZED">脱敏后流转（拆校徽工牌/涂销姓名）</el-radio>
              <el-radio value="REJECTED">拒收（不进入公益流转）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="sortForm.privacyAction==='DESENSITIZED' ? '脱敏措施说明' : '拒收依据说明'">
            <el-input v-model="sortForm.privacyNote" type="textarea"
                      :placeholder="sortForm.privacyAction==='DESENSITIZED'
                        ? '必填：逐条记录拆除/涂销了哪些个人标识（校徽、工牌、姓名标签等）'
                        : '必填：拒收原因与登记处置方式'" />
          </el-form-item>
          <el-form-item v-if="sortForm.privacyAction==='DESENSITIZED'" label="脱敏处理照片">
            <el-upload :http-request="uploadPrivacyPhoto" :show-file-list="false" accept="image/*">
              <el-button :icon="Picture">上传脱敏处理后复检照片（必传）</el-button>
            </el-upload>
            <el-image v-if="sortForm.privacyPhotoUrl" :src="sortForm.privacyPhotoUrl"
                      style="width:180px;margin-left:12px" />
            <div class="muted" v-if="!sortForm.privacyPhotoUrl">无处理照片不得流转入公益批</div>
          </el-form-item>
        </template>
        <el-form-item label="复核照片"><el-upload :http-request="uploadSortPhoto" :show-file-list="false" accept="image/*">
          <el-button :icon="Picture">上传分拣照片</el-button>
          <el-image v-if="sortForm.photoUrl" :src="sortForm.photoUrl" style="width:180px;margin-left:12px" />
        </el-upload></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sortDlg=false">取消</el-button>
        <el-button type="primary" @click="submitSort">提交复核</el-button>
      </template>
    </el-dialog>

    <!-- 派单 -->
    <el-dialog v-model="assignDlg" title="指派回收员" width="420px">
      <el-select v-model="assignCollectorId" placeholder="选择回收员" style="width:100%">
        <el-option v-for="c in collectors" :key="c.id" :label="`${c.displayName}（${c.communityName||'跨片区'}）`" :value="c.id" />
      </el-select>
      <template #footer>
        <el-button @click="assignDlg=false">取消</el-button>
        <el-button type="primary" @click="submitAssign">确认派单</el-button>
      </template>
    </el-dialog>

    <!-- 投诉 -->
    <el-dialog v-model="complaintDlg" title="对本回收单发起投诉/质疑" width="500px">
      <el-form label-position="top">
        <el-form-item label="投诉类型">
          <el-select v-model="complaintForm.type" style="width:100%">
            <el-option v-for="(label,k) in COMPLAINT_TYPE" :key="k" :label="label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="情况说明"><el-input v-model="complaintForm.description" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="complaintDlg=false">取消</el-button>
        <el-button type="warning" @click="submitComplaint">提交，平台将组织相关角色协同处理</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailDlg" size="520px" :title="`回收单 ${current?.code || ''}`">
      <template v-if="current">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="状态"><el-tag :type="ORDER_STATUS[current.status].type">{{ ORDER_STATUS[current.status].label }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="居民">{{ current.resident.displayName }}</el-descriptions-item>
          <el-descriptions-item label="回收员">{{ current.collector?.displayName || '待派单' }}</el-descriptions-item>
          <el-descriptions-item label="预约">{{ current.itemCount }}件 · {{ current.categories }} · {{ current.washed?'已清洗':'未清洗' }}{{ current.hasShoesBagsBedding?' · 含鞋包被褥':'' }}</el-descriptions-item>
          <el-descriptions-item label="地址时段">{{ current.address }} · {{ current.timeSlot }}</el-descriptions-item>
          <el-descriptions-item v-if="current.partner" label="公益活动">{{ current.partner.name }} · {{ current.partner.projectName }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">上门称重（回收员）</el-divider>
        <div v-if="current.pickup">
          <el-image v-if="current.pickup.photoUrl" :src="current.pickup.photoUrl" class="photo-thumb" style="max-height:220px" />
          <el-descriptions :column="1" border size="small" style="margin-top:8px">
            <el-descriptions-item label="称重">{{ current.pickup.weightKg }}kg</el-descriptions-item>
            <el-descriptions-item label="初步分类">{{ current.pickup.preCategories }}</el-descriptions-item>
            <el-descriptions-item label="时效"><el-tag size="small" :type="current.pickup.arrivedLate?'danger':'success'">{{ current.pickup.arrivedLate?'上门迟到':'准时' }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="居民确认">
              <el-tag size="small" :type="current.pickup.residentConfirmed?'success':'info'">
                {{ current.pickup.residentConfirmed ? confirmLabel(current.pickup.confirmOption) : '待确认' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="current.pickup.note" label="备注">{{ current.pickup.note }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <el-empty v-else description="回收员尚未上门" :image-size="60" />

        <el-divider content-position="left">分拣复核（分拣中心）</el-divider>
        <div v-if="current.sort">
          <el-image v-if="current.sort.photoUrl" :src="current.sort.photoUrl" class="photo-thumb" style="max-height:220px" />
          <el-descriptions :column="1" border size="small" style="margin-top:8px">
            <el-descriptions-item label="五分类">
              <el-tag :type="SORT_CATEGORY[current.sort.category].type">{{ SORT_CATEGORY[current.sort.category].label }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="重量差异">
              {{ current.sort.weightDiff }}kg
              <el-tag size="small" :type="Math.abs(Number(current.sort.weightDiff))>0.5?'warning':'info'">
                {{ Math.abs(Number(current.sort.weightDiff))>0.5?'差异偏大，建议核查':'正常误差范围' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="current.sort.damageReason" label="污损原因">{{ current.sort.damageReason }}</el-descriptions-item>
            <el-descriptions-item label="去向">{{ current.sort.destination }}</el-descriptions-item>
            <el-descriptions-item v-if="current.sort.privacyRisk" label="隐私处置">
              <el-tag :type="current.sort.privacyAction==='DESENSITIZED'?'success':'danger'" effect="dark">
                {{ current.sort.privacyConclusion?.label }}
              </el-tag>
              <div class="muted" style="margin-top:4px">{{ current.sort.privacyConclusion?.conclusion }}</div>
              <el-image v-if="current.sort.privacyPhotoUrl" :src="current.sort.privacyPhotoUrl"
                        class="photo-thumb" style="max-height:180px;margin-top:6px"
                        preview-src-list="[current.sort.privacyPhotoUrl]" />
            </el-descriptions-item>
            <el-descriptions-item label="分拣员">{{ current.sort.sorter.displayName }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <el-empty v-else description="等待分拣中心复核" :image-size="60" />

        <el-divider content-position="left">批次去向 / 关联投诉</el-divider>
        <div v-if="current.batch">
          批次 <b>{{ current.batch.code }}</b> · {{ current.batch.projectName || current.batch.recyclerName }}
          · <el-tag size="small" :type="BATCH_STATUS[current.batch.status].type">{{ BATCH_STATUS[current.batch.status].label }}</el-tag>
        </div>
        <div v-else class="muted">尚未集货入批</div>
        <div v-if="current.complaints?.length" style="margin-top:8px">
          <el-tag v-for="c in current.complaints" :key="c.id" type="warning" style="margin-right:6px">
            投诉#{{ c.id }} {{ COMPLAINT_TYPE[c.type] }} · {{ COMPLAINT_STATUS[c.status].label }}
          </el-tag>
          <el-button link type="primary" @click="$router.push('/app/complaints')">去处理 →</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Picture } from '@element-plus/icons-vue'
import api from '../api'
import { useAuth, ORDER_STATUS, SORT_CATEGORY, COMPLAINT_TYPE, COMPLAINT_STATUS, BATCH_STATUS } from '../store'

const auth = useAuth()
const role = computed(() => auth.role)

const orders = ref([])
const partners = ref([])
const collectors = ref([])
const statusFilter = ref('')
const kw = ref('')

const CAT_OPTIONS = ['上衣', '裤装', '外套', '童装', '鞋包', '被褥', '校服', '工作服', '旧衣', '其他']
const PRE_CATS = ['可直接捐赠', '需消毒整理', '环保再生', '待复核', '特殊处理']
const SLOTS = ['2026-09-17 09:00-11:00', '2026-09-17 14:00-16:00', '2026-09-18 09:00-11:00', '2026-09-18 14:00-16:00', '2026-09-19 09:00-11:00', '2026-09-19 19:00-21:00']

const filtered = computed(() => orders.value.filter(o =>
  !kw.value || o.code.includes(kw.value) || o.address.includes(kw.value) || o.communityName.includes(kw.value)))

async function load() {
  const url = '/api/orders' + (statusFilter.value ? `?status=${statusFilter.value}` : '')
  orders.value = await api.get(url)
}
onMounted(async () => {
  await load()
  if (role.value === 'RESIDENT') partners.value = await api.get('/api/partners')
  if (['COMMUNITY', 'ADMIN'].includes(role.value)) collectors.value = await api.get('/api/orders/collectors')
})

// ---------- 新建预约 ----------
const createDlg = ref(false)
const form = ref({})
function emptyForm() {
  return { itemCount: 5, catArr: ['上衣'], washed: true, hasShoesBagsBedding: false,
    address: '', timeSlot: '', donateWanted: true, partnerId: null }
}
function openCreate() { form.value = emptyForm(); createDlg.value = true }
async function submitCreate() {
  if (!form.value.address || !form.value.timeSlot) return ElMessage.warning('地址和时段必填')
  await api.post('/api/orders', {
    ...form.value, categories: form.value.catArr.join(',')
  })
  ElMessage.success('预约已提交，等待派单')
  createDlg.value = false
  load()
}

// ---------- 派单/抢单 ----------
async function assign(row) {
  await api.post(`/api/orders/${row.id}/assign`, {})
  ElMessage.success('抢单成功，请按时上门')
  load()
}
const assignDlg = ref(false)
const assignCollectorId = ref(null)
const current = ref(null)
function openAssign(row) { current.value = row; assignCollectorId.value = null; assignDlg.value = true }
async function submitAssign() {
  if (!assignCollectorId.value) return ElMessage.warning('请选择回收员')
  await api.post(`/api/orders/${current.value.id}/assign`, { collectorId: assignCollectorId.value })
  ElMessage.success('派单成功')
  assignDlg.value = false
  load()
}

// ---------- 上门登记 ----------
const pickupDlg = ref(false)
const pickupForm = ref({})
function openPickup(row) {
  current.value = row
  pickupForm.value = { weightKg: null, preCategories: '可直接捐赠', arrivedLate: false, note: '', photoUrl: '' }
  pickupDlg.value = true
}
async function uploadPickupPhoto(opt) {
  const fd = new FormData()
  fd.append('photo', opt.file)
  // 先暂存图片：借用 multipart 接口需要必填重量，故改为本地预览，提交时再上传
  pickupForm.value._file = opt.file
  pickupForm.value.photoUrl = URL.createObjectURL(opt.file)
}
async function submitPickup() {
  if (!pickupForm.value.weightKg) return ElMessage.warning('请输入称重')
  if (!pickupForm.value.preCategories) return ElMessage.warning('请填写初步分类')
  if (!pickupForm.value._file) return ElMessage.warning('必须上传现场称重/打包照片')
  const fd = new FormData()
  Object.entries(pickupForm.value).forEach(([k, v]) => {
    if (k[0] !== '_' && k !== 'photoUrl' && v !== null && v !== '') fd.append(k, v)
  })
  fd.append('photo', pickupForm.value._file)
  await api.post(`/api/orders/${current.value.id}/pickup`, fd)
  ElMessage.success('上门登记完成，等待居民确认')
  pickupDlg.value = false
  load()
}

// ---------- 居民确认 ----------
const confirmDlg = ref(false)
const confirmOption = ref('POINTS')
function openConfirm(row) { current.value = row; confirmOption.value = 'POINTS'; confirmDlg.value = true }
function estPoints(opt) {
  const w = Number(current.value?.pickup?.weightKg || 0)
  return Math.round(opt === 'MIXED' ? w * 25 : w * 50)
}
async function submitConfirm() {
  await api.post(`/api/orders/${current.value.id}/confirm`, { option: confirmOption.value })
  ElMessage.success('已确认，积分将实时入账（可在环保积分查看）')
  confirmDlg.value = false
  load()
}
function confirmLabel(o) { return { POINTS: '积分', DONATION: '公益捐赠', MIXED: '积分+捐赠' }[o] || o }

// ---------- 分拣复核 ----------
const sortDlg = ref(false)
const sortForm = ref({})
function openSort(row) {
  current.value = row
  sortForm.value = {
    category: 'DIRECT_DONATE', weightKg: Number(row.pickupWeight),
    damageReason: '', destination: '', privacyRisk: false, privacyAction: 'DESENSITIZED',
    privacyNote: '', photoUrl: '', privacyPhotoUrl: ''
  }
  sortDlg.value = true
}
async function uploadSortPhoto(opt) {
  sortForm.value._file = opt.file
  sortForm.value.photoUrl = URL.createObjectURL(opt.file)
}
async function uploadPrivacyPhoto(opt) {
  sortForm.value._privacyFile = opt.file
  sortForm.value.privacyPhotoUrl = URL.createObjectURL(opt.file)
}
async function submitSort() {
  const f = sortForm.value
  if (f.privacyRisk) {
    if (!f.privacyAction) return ElMessage.warning('隐私风险衣物必须选择脱敏后流转或拒收')
    if (!f.privacyNote?.trim()) {
      return ElMessage.warning(f.privacyAction === 'DESENSITIZED' ? '请填写脱敏措施说明' : '请填写拒收依据说明')
    }
    if (f.privacyAction === 'DESENSITIZED' && !f._privacyFile) {
      return ElMessage.warning('脱敏后流转必须上传处理后的复检照片')
    }
    if (f.privacyAction === 'DESENSITIZED'
        && f.category !== 'DIRECT_DONATE' && f.category !== 'NEED_CLEAN') {
      return ElMessage.warning('脱敏流转仅适用于可直接捐赠/需消毒整理类')
    }
  }
  const fd = new FormData()
  Object.entries(f).forEach(([k, v]) => {
    if (k[0] !== '_' && k !== 'photoUrl' && k !== 'privacyPhotoUrl' && v !== null && v !== '') fd.append(k, v)
  })
  if (f._file) fd.append('photo', f._file)
  if (f._privacyFile) fd.append('privacyPhoto', f._privacyFile)
  await api.post(`/api/orders/${current.value.id}/sort`, fd)
  ElMessage.success('复核完成')
  sortDlg.value = false
  load()
}

// ---------- 投诉 ----------
const complaintDlg = ref(false)
const complaintForm = ref({ type: 'WEIGHT_DISPUTE', description: '' })
function openComplaint(row) {
  current.value = row
  complaintForm.value = { type: 'WEIGHT_DISPUTE', description: '' }
  complaintDlg.value = true
}
async function submitComplaint() {
  if (!complaintForm.value.description) return ElMessage.warning('请填写情况说明')
  await api.post('/api/complaints', { orderId: current.value.id, ...complaintForm.value })
  ElMessage.success('投诉已受理，可在投诉协同页跟踪处理进展')
  complaintDlg.value = false
}

// ---------- 详情 ----------
const detailDlg = ref(false)
async function openDetail(row) {
  current.value = await api.get(`/api/orders/${row.id}`)
  detailDlg.value = true
}
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.muted { color: #97a0af; font-size: 12px; }
.confirm-opts { display: flex; flex-direction: column; gap: 12px; margin-top: 16px; }
.confirm-opts :deep(.el-radio) { margin: 0; padding: 10px 14px; height: auto; white-space: normal; }
</style>
