<template>
  <div>
    <h2 class="page-title">批次与去向</h2>
    <div class="page-sub">同去向集货发运，公益机构拍照签收；被拒收批次记录原因/复核照片，分拣中心重新分拣后改配公益或转环保再生，全程可追溯</div>

    <el-card class="section-card">
      <div class="toolbar">
        <el-radio-group v-model="typeFilter" size="small" @change="load">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="DONATION">公益捐赠</el-radio-button>
          <el-radio-button label="RECYCLE">环保再生</el-radio-button>
        </el-radio-group>
        <el-select v-model="statusFilter" placeholder="全部状态" clearable size="small" style="width:160px" @change="load">
          <el-option v-for="(v,k) in BATCH_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-checkbox v-model="onlyRejected" border size="small" @change="load">仅看拒收处置</el-checkbox>
        <div style="flex:1"></div>
        <el-button v-if="['SORTER','ADMIN'].includes(role)" type="primary" :icon="Plus" @click="openCreate">新建批次并入批</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-row :gutter="14">
        <el-col :xs="24" :md="12" v-for="b in shown" :key="b.id">
          <el-card class="b-card" :class="{rejected: b.status==='REJECTED'}">
            <div class="bc-head">
              <div>
                <el-tag :type="b.batchType==='RECYCLE'?'primary':'success'" size="small" effect="dark">
                  {{ b.batchType==='RECYCLE' ? '环保再生' : '公益捐赠' }}
                </el-tag>
                <b style="margin-left:8px">{{ b.code }}</b>
                <el-tag v-if="b.sourceBatch" size="small" type="warning" style="margin-left:6px">
                  拒收改配自 {{ b.sourceBatch.code }}
                </el-tag>
              </div>
              <el-tag size="small" :type="BATCH_STATUS[b.status].type">{{ BATCH_STATUS[b.status].label }}</el-tag>
            </div>
            <div class="dest">
              {{ b.batchType==='RECYCLE' ? '再生厂：' + (b.recyclerName||'-') : '项目：' + (b.projectName||'-') + '（' + (b.organization?.organizationName||'-') + '）' }}
            </div>
            <div v-if="b.partner" class="partner">🏷️ {{ b.partner.type==='SCHOOL'?'学校捐衣活动':'企业公益合作' }} · {{ b.partner.name }}</div>

            <div class="nums">
              <div><b>{{ b.totalWeightKg }}</b>kg 总重</div>
              <div><b>{{ b.itemCount }}</b> 件</div>
              <div><b>{{ b.orderCount }}</b> 张回收单</div>
              <div v-if="b.recycledWeightKg!=null"><b>{{ b.recycledWeightKg }}</b>kg 再生量</div>
            </div>

            <el-row :gutter="8" v-if="b.donationPhotoUrl || b.signPhotoUrl">
              <el-col :span="12" v-if="b.donationPhotoUrl">
                <el-image :src="b.donationPhotoUrl" class="ph" fit="cover" :preview-src-list="[b.donationPhotoUrl]" preview-teleported />
                <div class="cap">捐赠/装车照片</div>
              </el-col>
              <el-col :span="12" v-if="b.signPhotoUrl">
                <el-image :src="b.signPhotoUrl" class="ph" fit="cover" :preview-src-list="[b.signPhotoUrl]" preview-teleported />
                <div class="cap">机构签收照片 · {{ b.receiverName }}</div>
              </el-col>
            </el-row>

            <div v-if="b.signNote" class="note">✍️ 签收备注：{{ b.signNote }}</div>

            <!-- 拒收证据 -->
            <div v-if="b.status==='REJECTED' || b.rejectReason" class="reject-box">
              <div class="rb-title">⛔ 公益机构拒收 · {{ b.rejectReasonTypeLabel || '其他' }}</div>
              <div class="rb-reason">{{ b.rejectReason }}</div>
              <el-image v-if="b.rejectPhotoUrl" :src="b.rejectPhotoUrl" class="ph rb-ph" fit="cover"
                        :preview-src-list="[b.rejectPhotoUrl]" preview-teleported />
            </div>

            <!-- 重新分拣结论 -->
            <div v-if="b.resortAt" class="resort-box">
              <div class="rs-title">🔄 分拣中心重新分拣 · {{ b.resortSummary }}</div>
              <div class="note">{{ b.resortReason }}</div>
              <div class="note">可继续处置重量：<b>{{ b.resortWeightKg }}</b>kg（原批 {{ b.totalWeightKg }}kg）· 分拣员 {{ b.resortSorter?.displayName }}</div>
              <el-image v-if="b.resortPhotoUrl" :src="b.resortPhotoUrl" class="ph rb-ph" fit="cover"
                        :preview-src-list="[b.resortPhotoUrl]" preview-teleported />
              <div v-for="r in b.resortRecords" :key="r.orderId" class="rr-line">
                · {{ r.orderCode }} →
                <el-tag size="small" :type="RESORT_OUTCOME[r.outcome].type">{{ RESORT_OUTCOME[r.outcome].label }}</el-tag>
                {{ r.newCategory==='ECO_RECYCLE' ? '环保再生' : r.newCategory==='DIRECT_DONATE' ? '可直接捐赠' : r.newCategory==='NEED_CLEAN' ? '需消毒整理' : r.newCategory }}
                {{ r.weightKg }}kg（差异 {{ r.weightDiff }}kg）
              </div>
            </div>

            <!-- 再分配流向链 -->
            <div v-if="b.redistributions?.length" class="chain">
              <div class="ch-title">➡️ 再分配流向（{{ b.redistributions.length }}）</div>
              <div v-for="c in b.redistributions" :key="c.id" class="ch-line">
                <el-tag size="small" :type="c.batchType==='RECYCLE'?'primary':'success'">
                  {{ c.batchType==='RECYCLE' ? '转环保再生' : '改配公益' }}
                </el-tag>
                {{ c.code }} · {{ c.projectName || c.recyclerName }} ·
                {{ BATCH_STATUS[c.status].label }}
                <span v-if="c.recycledWeightKg!=null"> · 再生 {{ c.recycledWeightKg }}kg</span>
              </div>
            </div>

            <div v-if="b.publicNote" class="note">📝 {{ b.publicNote }}</div>

            <!-- 构成 -->
            <el-collapse>
              <el-collapse-item :title="`本批 ${b.orderCount} 张回收单${b.hiddenOrderCount?`（${b.hiddenOrderCount} 位居民要求匿名）`:''}`" :name="1">
                <div v-for="o in b.orders" :key="o.id" class="orders-mini-line">
                  <el-tag v-if="o.anonymous" size="small" type="info" effect="plain">匿名住户</el-tag>
                  <template v-else>
                    <b>{{ o.code }}</b> · {{ o.communityName }}
                    <el-tag size="small" :type="SORT_CATEGORY[o.category]?.type" effect="plain">
                      {{ SORT_CATEGORY[o.category]?.label }}
                    </el-tag>
                    <el-tag v-if="o.privacy" size="small" type="success" effect="dark">🔒{{ o.privacy.label }}</el-tag>
                  </template>
                  <span class="w">{{ o.weightKg }}kg</span>
                </div>
              </el-collapse-item>
            </el-collapse>

            <div class="actions">
              <template v-if="['SORTER','ADMIN'].includes(role)">
                <el-upload v-if="b.status==='STAGED'" :http-request="(opt)=>uploadDonation(b.id,opt)"
                           :show-file-list="false" accept="image/*">
                  <el-button size="small" :icon="Picture">捐赠照片</el-button>
                </el-upload>
                <el-button v-if="b.status==='STAGED'" size="small" type="primary" @click="ship(b)">发运</el-button>
                <el-button v-if="b.status==='REJECTED' && !b.resortAt" size="small" type="warning" @click="openResort(b)">
                  重新分拣处置
                </el-button>
                <el-button v-if="b.status==='REJECTED' && b.resortAt" size="small" type="success" @click="openCreate(b)">
                  建立再分配批次
                </el-button>
                <el-button v-if="b.status==='IN_TRANSIT' && b.batchType==='RECYCLE'" size="small" type="primary" @click="openRecycle(b)">
                  登记再生处理量
                </el-button>
              </template>
              <template v-if="role==='ORG' && b.status==='IN_TRANSIT' && b.batchType==='DONATION'">
                <el-button size="small" type="success" @click="openSign(b)">验收签收</el-button>
                <el-button size="small" type="danger" plain @click="openReject(b)">拒收并留证</el-button>
              </template>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!batches.length" description="暂无批次" />
    </el-card>

    <!-- 新建批次（可选择拒收来源批次） -->
    <el-dialog v-model="createDlg" :title="cf.sourceBatchId ? '建立拒收再分配批次' : '新建批次（选择已分拣回收单集货）'" width="760px" top="5vh">
      <el-form label-width="100px">
        <el-form-item v-if="cf.sourceBatchId" label="来源拒收批">
          <el-tag type="danger">{{ sourceBatch?.code }} · {{ sourceBatch?.rejectReasonTypeLabel }}</el-tag>
          <span class="muted" style="margin-left:8px">仅可选择该拒收批重新分拣后的衣物</span>
        </el-form-item>
        <el-form-item label="批次类型">
          <el-radio-group v-model="cf.batchType">
            <el-radio-button value="DONATION" :disabled="!!cf.sourceBatchId">公益捐赠批</el-radio-button>
            <el-radio-button value="RECYCLE" :disabled="!!cf.sourceBatchId">环保再生批</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="cf.batchType==='DONATION'" label="公益机构">
          <el-select v-model="cf.organizationId" style="width:100%" placeholder="选择去向机构">
            <el-option v-for="o in orgs" :key="o.id" :label="o.organizationName" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="cf.batchType==='DONATION'" label="公益项目">
          <el-input v-model="cf.projectName" placeholder="如：暖阳低年级冬季衣物包" />
        </el-form-item>
        <el-form-item v-if="cf.batchType==='RECYCLE'" label="再生处理厂">
          <el-input v-model="cf.recyclerName" placeholder="如：绿纤环保再生资源厂" />
        </el-form-item>
        <el-form-item label="关联活动">
          <el-select v-model="cf.partnerId" clearable style="width:100%" placeholder="可选">
            <el-option v-for="p in partners" :key="p.id"
                       :label="`${p.type==='SCHOOL'?'学校':'企业'} · ${p.name} · ${p.projectName||''}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="公示说明"><el-input v-model="cf.publicNote" type="textarea" :rows="2"
          :placeholder="cf.sourceBatchId ? '向居民说明拒收后的替代去向（原因、处理机构、重量变化）' : '面向居民的公示说明'" /></el-form-item>
        <el-form-item label="选择回收单">
          <el-table :data="sortedOrders" max-height="260" size="small" style="width:100%"
                    @selection-change="rows => cf.orderIds = rows.map(r=>r.id)">
            <el-table-column type="selection" width="44" />
            <el-table-column prop="code" label="单号" width="110" />
            <el-table-column prop="communityName" label="小区" width="120" />
            <el-table-column label="分类" width="120">
              <template #default="{row}">
                <el-tag size="small" :type="SORT_CATEGORY[row.sort.category].type">{{ SORT_CATEGORY[row.sort.category].label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="重量" width="90">
              <template #default="{row}">{{ row.sort.weightKg }}kg</template>
            </el-table-column>
            <el-table-column label="去向/隐私">
              <template #default="{row}">
                <span v-if="row.sort.privacyRisk" class="privacy-tag">🔒 {{ row.sort.privacyConclusion?.label }} ·证据✓</span>
                <span v-else>{{ row.sort.destination }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDlg=false">取消</el-button>
        <el-button type="primary" @click="submitCreate">{{ cf.sourceBatchId ? '建立再分配批次' : '集货建批' }}</el-button>
      </template>
    </el-dialog>

    <!-- 机构拒收 -->
    <el-dialog v-model="rejectDlg" title="公益机构拒收（留证）" width="520px">
      <el-alert type="warning" :closable="false" class="mb"
                title="拒收将通知分拣中心重新分拣，居民可在公示端看到拒收原因与最终替代去向" />
      <el-form label-width="100px">
        <el-form-item label="拒收类型" required>
          <el-radio-group v-model="rf.reasonType">
            <el-radio v-for="(label,k) in REJECT_REASON" :key="k" :value="k">{{ label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="拒收原因" required>
          <el-input v-model="rf.reason" type="textarea" :rows="3" placeholder="尺码/季节/卫生标准不匹配的具体情况" />
        </el-form-item>
        <el-form-item label="现场复核照片" required>
          <input type="file" accept="image/*" @change="onRejectFile" />
          <el-image v-if="rf.photoUrl" :src="rf.photoUrl" style="width:200px;margin-top:8px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDlg=false">取消</el-button>
        <el-button type="danger" @click="submitReject">确认拒收并退回</el-button>
      </template>
    </el-dialog>

    <!-- 重新分拣处置 -->
    <el-dialog v-model="resortDlg" title="拒收退回 · 重新分拣处置" width="780px" top="5vh">
      <el-form label-width="120px">
        <el-form-item label="总体结论">
          <el-radio-group v-model="rsf.summary">
            <el-radio-button value="重新整理后改配公益机构">改配公益</el-radio-button>
            <el-radio-button value="不达捐赠卫生标准，转环保再生">转环保再生</el-radio-button>
            <el-radio-button value="混合处置">混合处置</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="重新分拣说明">
          <el-input v-model="rsf.reason" type="textarea" :rows="2"
            placeholder="向居民说明：为何拒收、如何重新分拣、重量因何变化，避免误解为捐赠失败" />
        </el-form-item>
      </el-form>
      <el-table :data="rsf.entries" size="small" border>
        <el-table-column prop="code" label="回收单" width="110" />
        <el-table-column label="原分类/重量" width="130">
          <template #default="{row}">{{ row.oldCategory }} / {{ row.oldWeight }}kg</template>
        </el-table-column>
        <el-table-column label="新分类" width="150">
          <template #default="{row}">
            <el-select v-model="row.newCategory" size="small">
              <el-option label="可直接捐赠" value="DIRECT_DONATE" />
              <el-option label="需消毒整理" value="NEED_CLEAN" />
              <el-option label="环保再生" value="ECO_RECYCLE" />
              <el-option label="不可回收" value="NON_RECYCLABLE" />
              <el-option label="特殊处理" value="SPECIAL" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="重量kg" width="120">
          <template #default="{row}"><el-input-number v-model="row.weightKg" :min="0.01" :precision="2" :step="0.1" size="small" controls-position="right" style="width:105px" /></template>
        </el-table-column>
        <el-table-column label="处置去向" width="160">
          <template #default="{row}">
            <el-select v-model="row.outcome" size="small">
              <el-option label="改配公益" value="REDONATE" />
              <el-option label="转环保再生" value="TO_RECYCLE" />
              <el-option label="无害化处理" value="FINAL_REJECT" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="逐单原因" min-width="160">
          <template #default="{row}"><el-input v-model="row.reason" size="small" placeholder="原因/重量变化说明" /></template>
        </el-table-column>
      </el-table>
      <el-form label-width="120px" style="margin-top:12px">
        <el-form-item label="重新分拣照片">
          <input type="file" accept="image/*" @change="onResortFile" />
          <el-image v-if="rsf.photoUrl" :src="rsf.photoUrl" style="width:180px;margin-left:10px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resortDlg=false">取消</el-button>
        <el-button type="warning" @click="submitResort">提交重新分拣结论</el-button>
      </template>
    </el-dialog>

    <!-- 机构签收 -->
    <el-dialog v-model="signDlg" title="公益机构验收签收" width="480px">
      <el-form label-width="90px">
        <el-form-item label="签收人"><el-input v-model="sf.receiverName" /></el-form-item>
        <el-form-item label="验收备注"><el-input v-model="sf.signNote" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="签收照片">
          <input type="file" accept="image/*" @change="onSignFile" />
          <el-image v-if="sf.photoUrl" :src="sf.photoUrl" style="width:200px;margin-top:8px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="signDlg=false">取消</el-button>
        <el-button type="success" @click="submitSign">确认签收</el-button>
      </template>
    </el-dialog>

    <!-- 再生登记 -->
    <el-dialog v-model="recDlg" title="环保再生处理登记" width="460px">
      <el-form label-width="110px">
        <el-form-item label="再生处理量kg"><el-input-number v-model="rf2.recycledWeightKg" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="公示说明"><el-input v-model="rf2.publicNote" type="textarea" :rows="3"
          placeholder="再生工艺、产物去向与处置证明编号" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recDlg=false">取消</el-button>
        <el-button type="primary" @click="submitRecycle">登记并公示</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Picture } from '@element-plus/icons-vue'
import api from '../api'
import { useAuth, BATCH_STATUS, SORT_CATEGORY, REJECT_REASON, RESORT_OUTCOME } from '../store'

const auth = useAuth()
const role = computed(() => auth.role)
const batches = ref([])
const partners = ref([])
const orgs = ref([])
const typeFilter = ref('')
const statusFilter = ref('')
const onlyRejected = ref(false)

const CAT_LABEL = { DIRECT_DONATE: '可直接捐赠', NEED_CLEAN: '需消毒整理', ECO_RECYCLE: '环保再生', NON_RECYCLABLE: '不可回收', SPECIAL: '特殊处理' }

const shown = computed(() => {
  let d = typeFilter.value ? batches.value.filter(b => b.batchType === typeFilter.value) : batches.value
  if (statusFilter.value) d = d.filter(b => b.status === statusFilter.value)
  if (onlyRejected.value) d = d.filter(b => b.status === 'REJECTED' || b.sourceBatch)
  return d
})

async function load() {
  const data = await api.get('/api/batches')
  let d = data
  if (typeFilter.value) d = d.filter(b => b.batchType === typeFilter.value)
  if (statusFilter.value) d = d.filter(b => b.status === statusFilter.value)
  if (onlyRejected.value) d = d.filter(b => b.status === 'REJECTED' || b.sourceBatch)
  batches.value = d
}
onMounted(async () => {
  await load()
  if (['SORTER', 'ADMIN'].includes(role.value)) {
    partners.value = await api.get('/api/partners')
    orgs.value = await api.get('/api/batches/organizations')
  }
})

// 建批
const createDlg = ref(false)
const cf = ref({})
const sortedOrders = ref([])
const sourceBatch = ref(null)
async function openCreate(source) {
  cf.value = { batchType: source ? '' : 'DONATION', orderIds: [], organizationId: null,
    projectName: '', recyclerName: '', partnerId: null, publicNote: '', sourceBatchId: null }
  sortedOrders.value = []
  sourceBatch.value = null
  if (source) {
    cf.value.sourceBatchId = source.id
    sourceBatch.value = source
    const hasRecycle = source.resortRecords.some(r => r.outcome === 'TO_RECYCLE')
    const hasRedonate = source.resortRecords.some(r => r.outcome === 'REDONATE')
    cf.value.batchType = hasRecycle && !hasRedonate ? 'RECYCLE' : 'DONATION'
    if (cf.value.batchType === 'RECYCLE') cf.value.recyclerName = '绿纤环保再生资源厂'
    await loadSorted()
  }
  createDlg.value = true
}
async function loadSorted() {
  const q = new URLSearchParams({ batchType: cf.value.batchType })
  if (cf.value.sourceBatchId) q.set('sourceBatchId', cf.value.sourceBatchId)
  sortedOrders.value = await api.get('/api/batches/sorted-orders?' + q)
}
watch(() => cf.value.batchType, () => { if (createDlg.value) loadSorted() })
async function submitCreate() {
  if (!cf.value.orderIds.length) return ElMessage.warning('请勾选回收单')
  if (cf.value.batchType === 'DONATION' && !cf.value.organizationId) return ElMessage.warning('请选择公益机构')
  await api.post('/api/batches', cf.value)
  ElMessage.success(cf.value.sourceBatchId ? '再分配批次已建立' : '批次已建立（集货中）')
  createDlg.value = false
  load()
}

async function uploadDonation(id, opt) {
  const fd = new FormData(); fd.append('photo', opt.file)
  await api.post(`/api/batches/${id}/donation-photo`, fd)
  ElMessage.success('捐赠照片已上传'); load()
}
async function ship(b) {
  await ElMessageBox.confirm(`确认发运批次 ${b.code}？`, '发运确认', { type: 'warning' })
  await api.post(`/api/batches/${b.id}/ship`, {})
  ElMessage.success('已发运'); load()
}

// 拒收
const rejectDlg = ref(false)
const rf = ref({})
let rejectTarget = null
function openReject(b) {
  rejectTarget = b
  rf.value = { reasonType: 'SIZE_MISMATCH', reason: '', photoUrl: '', _file: null }
  rejectDlg.value = true
}
function onRejectFile(e) { rf.value._file = e.target.files[0]; rf.value.photoUrl = URL.createObjectURL(e.target.files[0]) }
async function submitReject() {
  if (!rf.value.reasonType) return ElMessage.warning('请选择拒收类型')
  if (!rf.value.reason.trim()) return ElMessage.warning('请填写拒收原因')
  if (!rf.value._file) return ElMessage.warning('必须上传现场复核照片')
  const fd = new FormData()
  fd.append('rejectReasonType', rf.value.reasonType)
  fd.append('reason', rf.value.reason)
  fd.append('rejectPhoto', rf.value._file)
  await api.post(`/api/batches/${rejectTarget.id}/reject`, fd)
  ElMessage.success('已拒收留证，批次退回分拣中心重新分拣')
  rejectDlg.value = false
  load()
}

// 重新分拣
const resortDlg = ref(false)
const rsf = ref({})
let resortTarget = null
async function openResort(b) {
  resortTarget = b
  const detail = await api.get(`/api/batches/${b.id}`)
  rsf.value = {
    summary: '重新整理后改配公益机构', reason: '', photoUrl: '', _file: null,
    entries: detail.orders.map(o => ({
      orderId: o.id, code: o.code,
      oldCategory: CAT_LABEL[o.category] || o.category, oldWeight: o.weightKg,
      newCategory: o.category === 'NEED_CLEAN' ? 'DIRECT_DONATE' : o.category,
      weightKg: Number(o.weightKg), outcome: 'REDONATE', reason: ''
    }))
  }
  resortDlg.value = true
}
function onResortFile(e) { rsf.value._file = e.target.files[0]; rsf.value.photoUrl = URL.createObjectURL(e.target.files[0]) }
async function submitResort() {
  if (!rsf.value.reason.trim()) return ElMessage.warning('请填写重新分拣说明')
  if (!rsf.value._file) return ElMessage.warning('请上传重新分拣复核照片')
  for (const e of rsf.value.entries) {
    if (e.outcome === 'TO_RECYCLE' && e.newCategory !== 'ECO_RECYCLE')
      return ElMessage.warning(`${e.code} 转环保再生时新分类必须为环保再生`)
  }
  const fd = new FormData()
  fd.append('entries', JSON.stringify(rsf.value.entries))
  fd.append('resortSummary', rsf.value.summary)
  fd.append('resortReason', rsf.value.reason)
  fd.append('resortPhoto', rsf.value._file)
  await api.post(`/api/batches/${resortTarget.id}/resort`, fd)
  ElMessage.success('重新分拣完成，可建立再分配批次')
  resortDlg.value = false
  load()
}

// 签收
const signDlg = ref(false)
const sf = ref({})
let signTarget = null
function openSign(b) {
  signTarget = b
  sf.value = { receiverName: auth.user.displayName, signNote: '', photoUrl: '', _file: null }
  signDlg.value = true
}
function onSignFile(e) { sf.value._file = e.target.files[0]; sf.value.photoUrl = URL.createObjectURL(e.target.files[0]) }
async function submitSign() {
  if (!sf.value.receiverName) return ElMessage.warning('请填写签收人')
  const fd = new FormData()
  fd.append('receiverName', sf.value.receiverName)
  fd.append('signNote', sf.value.signNote || '')
  if (sf.value._file) fd.append('photo', sf.value._file)
  await api.post(`/api/batches/${signTarget.id}/sign`, fd)
  ElMessage.success('签收成功，已对居民公示'); signDlg.value = false; load()
}

// 再生登记
const recDlg = ref(false)
const rf2 = ref({})
let recTarget = null
function openRecycle(b) { recTarget = b; rf2.value = { recycledWeightKg: Number(b.totalWeightKg), publicNote: '' }; recDlg.value = true }
async function submitRecycle() {
  await api.post(`/api/batches/${recTarget.id}/recycle-json`, rf2.value)
  ElMessage.success('再生处理量已登记并公示'); recDlg.value = false; load()
}
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.b-card { margin-bottom: 14px; }
.b-card.rejected { border-color: #fbc4c4; }
.bc-head { display: flex; justify-content: space-between; align-items: center; }
.dest { font-weight: 600; margin: 8px 0 2px; font-size: 13px; }
.partner { color: #b07d2b; font-size: 12px; margin-bottom: 6px; }
.nums { display: flex; gap: 18px; color: #7a869a; font-size: 12px; margin: 6px 0; flex-wrap: wrap; }
.nums b { color: var(--brand-dark); font-size: 15px; margin-right: 2px; }
.ph { width: 100%; height: 150px; border-radius: 8px; display: block; }
.cap { font-size: 11px; color: #97a0af; text-align: center; margin: 2px 0 6px; }
.reject-box { background: #fef0f0; border: 1px solid #fbc4c4; border-radius: 8px; padding: 8px 10px; margin: 6px 0; }
.rb-title { color: #c45656; font-weight: 700; font-size: 13px; }
.rb-reason { font-size: 12px; margin: 4px 0; }
.rb-ph { height: 130px; }
.resort-box { background: #fdf6ec; border: 1px solid #f5dab1; border-radius: 8px; padding: 8px 10px; margin: 6px 0; }
.rs-title { color: #b88230; font-weight: 700; font-size: 13px; margin-bottom: 4px; }
.rr-line { font-size: 12px; margin: 3px 0; color: #5f6b7a; display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.chain { background: #f0f7f4; border: 1px solid #cde7dc; border-radius: 8px; padding: 8px 10px; margin: 6px 0; }
.ch-title { font-weight: 700; font-size: 13px; color: #2f8f6b; margin-bottom: 4px; }
.ch-line { font-size: 12px; margin: 3px 0; color: #3a5a4a; display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.note { font-size: 12px; color: #5f6b7a; margin-top: 4px; }
.orders-mini-line { font-size: 12px; padding: 3px 0; display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.orders-mini-line .w { margin-left: auto; color: #64705a; }
.actions { display: flex; gap: 8px; border-top: 1px dashed #e8ecf0; padding-top: 10px; flex-wrap: wrap; }
.muted { color: #97a0af; font-size: 12px; }
.mb { margin-bottom: 12px; }
</style>
