<template>
  <div>
    <h2 class="page-title">批次与去向</h2>
    <div class="page-sub">同去向集货发运，公益机构拍照签收；环保再生批次公示处理量；拒收批次退回重新安排去向</div>

    <el-card class="section-card">
      <div class="toolbar">
        <el-radio-group v-model="typeFilter" size="small" @change="load">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="DONATION">公益捐赠</el-radio-button>
          <el-radio-button label="RECYCLE">环保再生</el-radio-button>
        </el-radio-group>
        <el-select v-model="statusFilter" placeholder="全部状态" clearable size="small" style="width:150px" @change="load">
          <el-option v-for="(v,k) in BATCH_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
        <div style="flex:1"></div>
        <el-button v-if="['SORTER','ADMIN'].includes(role)" type="primary" :icon="Plus" @click="openCreate">新建批次并入批</el-button>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-row :gutter="14">
        <el-col :xs="24" :md="12" v-for="b in batches" :key="b.id">
          <el-card class="b-card">
            <div class="bc-head">
              <div>
                <el-tag :type="b.batchType==='RECYCLE'?'primary':'success'" size="small" effect="dark">
                  {{ b.batchType==='RECYCLE' ? '环保再生' : '公益捐赠' }}
                </el-tag>
                <b style="margin-left:8px">{{ b.code }}</b>
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
                <el-image :src="b.donationPhotoUrl" class="ph" fit="cover" />
                <div class="cap">捐赠/装车照片</div>
              </el-col>
              <el-col :span="12" v-if="b.signPhotoUrl">
                <el-image :src="b.signPhotoUrl" class="ph" fit="cover" />
                <div class="cap">机构签收照片 · {{ b.receiverName }}</div>
              </el-col>
            </el-row>

            <div v-if="b.rejectReason" class="reject">⛔ 拒收原因：{{ b.rejectReason }}（回收单已退回分拣环节重新安排去向）</div>
            <div v-if="b.publicNote" class="note">📝 {{ b.publicNote }}</div>
            <div v-if="b.signNote" class="note">✍️ 签收备注：{{ b.signNote }}</div>

            <div class="orders-mini">
              <el-tag v-for="o in b.orders" :key="o.id" size="small" class="otag"
                      :type="SORT_CATEGORY[o.category]?.type" effect="plain">
                {{ o.code }} {{ o.weightKg }}kg
              </el-tag>
            </div>

            <div class="actions">
              <template v-if="['SORTER','ADMIN'].includes(role)">
                <el-upload v-if="b.status==='STAGED'" :http-request="(opt)=>uploadDonation(b.id,opt)"
                           :show-file-list="false" accept="image/*">
                  <el-button size="small" :icon="Picture">捐赠照片</el-button>
                </el-upload>
                <el-button v-if="b.status==='STAGED'" size="small" type="primary" @click="ship(b)">发运</el-button>
                <el-button v-if="b.status==='IN_TRANSIT' && b.batchType==='RECYCLE'" size="small" type="primary" @click="openRecycle(b)">
                  登记再生处理量
                </el-button>
              </template>
              <template v-if="role==='ORG' && b.status==='IN_TRANSIT' && b.batchType==='DONATION'">
                <el-button size="small" type="success" @click="openSign(b)">验收签收</el-button>
                <el-button size="small" type="danger" plain @click="reject(b)">拒收并说明</el-button>
              </template>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!batches.length" description="暂无批次" />
    </el-card>

    <!-- 新建批次 -->
    <el-dialog v-model="createDlg" title="新建批次（选择已分拣回收单集货）" width="720px" top="6vh">
      <el-form label-width="100px">
        <el-form-item label="批次类型">
          <el-radio-group v-model="cf.batchType" @change="loadSorted">
            <el-radio-button value="DONATION">公益捐赠批</el-radio-button>
            <el-radio-button value="RECYCLE">环保再生批</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="cf.batchType==='DONATION'" label="公益机构">
          <el-select v-model="cf.organizationId" style="width:100%" placeholder="选择去向机构">
            <el-option v-for="o in orgs" :key="o.id" :label="o.organizationName" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="cf.batchType==='DONATION'" label="公益项目">
          <el-input v-model="cf.projectName" placeholder="如：打工子弟冬季温暖包" />
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
        <el-form-item label="公示说明"><el-input v-model="cf.publicNote" type="textarea" :rows="2" /></el-form-item>
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
                <span v-if="row.sort.privacyAction==='DESENSITIZED'" class="privacy-tag">✂️ 已脱敏</span>
                <span v-else>{{ row.sort.destination }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDlg=false">取消</el-button>
        <el-button type="primary" @click="submitCreate">集货建批</el-button>
      </template>
    </el-dialog>

    <!-- 机构签收 -->
    <el-dialog v-model="signDlg" title="公益机构验收签收" width="480px">
      <el-form label-width="90px">
        <el-form-item label="签收人"><el-input v-model="sf.receiverName" /></el-form-item>
        <el-form-item label="验收备注"><el-input v-model="sf.signNote" type="textarea" :rows="2"
          placeholder="清点件数、质量情况；签收后对居民公示" /></el-form-item>
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
        <el-form-item label="再生处理量kg"><el-input-number v-model="rf.recycledWeightKg" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="公示说明"><el-input v-model="rf.publicNote" type="textarea" :rows="3"
          placeholder="再生工艺（如开松纤维化）与产物去向、处置证明编号" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recDlg=false">取消</el-button>
        <el-button type="primary" @click="submitRecycle">登记并公示</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Picture } from '@element-plus/icons-vue'
import api from '../api'
import { useAuth, BATCH_STATUS, SORT_CATEGORY } from '../store'

const auth = useAuth()
const role = computed(() => auth.role)
const batches = ref([])
const partners = ref([])
const orgs = ref([])
const typeFilter = ref('')
const statusFilter = ref('')

async function load() {
  const p = new URLSearchParams()
  if (statusFilter.value) p.set('status', statusFilter.value)
  const data = await api.get('/api/batches' + (p.toString() ? '?' + p : ''))
  batches.value = typeFilter.value ? data.filter(b => b.batchType === typeFilter.value) : data
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
function openCreate() {
  cf.value = { batchType: 'DONATION', orderIds: [], organizationId: null, projectName: '', recyclerName: '', partnerId: null, publicNote: '' }
  sortedOrders.value = []
  createDlg.value = true
  loadSorted()
}
async function loadSorted() {
  sortedOrders.value = await api.get(`/api/batches/sorted-orders?batchType=${cf.value.batchType}`)
}
async function submitCreate() {
  if (!cf.value.orderIds.length) return ElMessage.warning('请勾选回收单')
  if (cf.value.batchType === 'DONATION' && !cf.value.organizationId) return ElMessage.warning('请选择公益机构')
  await api.post('/api/batches', cf.value)
  ElMessage.success('批次已建立（集货中），上传捐赠照片后即可发运')
  createDlg.value = false
  load()
}

async function uploadDonation(id, opt) {
  const fd = new FormData()
  fd.append('photo', opt.file)
  await api.post(`/api/batches/${id}/donation-photo`, fd)
  ElMessage.success('捐赠照片已上传')
  load()
}

async function ship(b) {
  await ElMessageBox.confirm(`确认发运批次 ${b.code}？发运后进入公示，状态变更为运输在途`, '发运确认', { type: 'warning' })
  await api.post(`/api/batches/${b.id}/ship`, {})
  ElMessage.success('已发运')
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
function onSignFile(e) {
  sf.value._file = e.target.files[0]
  sf.value.photoUrl = URL.createObjectURL(e.target.files[0])
}
async function submitSign() {
  if (!sf.value.receiverName) return ElMessage.warning('请填写签收人')
  const fd = new FormData()
  fd.append('receiverName', sf.value.receiverName)
  fd.append('signNote', sf.value.signNote || '')
  if (sf.value._file) fd.append('photo', sf.value._file)
  await api.post(`/api/batches/${signTarget.id}/sign`, fd)
  ElMessage.success('签收成功，已对居民公示')
  signDlg.value = false
  load()
}

async function reject(b) {
  const { value } = await ElMessageBox.prompt('请说明拒收原因（将同步居民与分拣中心）', '公益机构拒收', {
    confirmButtonText: '确认拒收', cancelButtonText: '取消', type: 'warning', inputType: 'textarea'
  })
  await api.post(`/api/batches/${b.id}/reject`, { reason: value })
  ElMessage.success('已拒收，回收单退回分拣环节')
  load()
}

// 再生登记
const recDlg = ref(false)
const rf = ref({})
let recTarget = null
function openRecycle(b) {
  recTarget = b
  rf.value = { recycledWeightKg: Number(b.totalWeightKg), publicNote: '' }
  recDlg.value = true
}
async function submitRecycle() {
  await api.post(`/api/batches/${recTarget.id}/recycle-json`, rf.value)
  ElMessage.success('再生处理量已登记并公示')
  recDlg.value = false
  load()
}
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.b-card { margin-bottom: 14px; }
.bc-head { display: flex; justify-content: space-between; align-items: center; }
.dest { font-weight: 600; margin: 8px 0 2px; font-size: 13px; }
.partner { color: #b07d2b; font-size: 12px; margin-bottom: 6px; }
.nums { display: flex; gap: 18px; color: #7a869a; font-size: 12px; margin: 6px 0; }
.nums b { color: var(--brand-dark); font-size: 15px; margin-right: 2px; }
.ph { width: 100%; height: 150px; border-radius: 8px; display: block; }
.cap { font-size: 11px; color: #97a0af; text-align: center; margin: 2px 0 6px; }
.reject { background: #fef0f0; color: #c45656; border-radius: 6px; padding: 6px 10px; font-size: 12px; margin: 6px 0; }
.note { font-size: 12px; color: #5f6b7a; margin-top: 4px; }
.orders-mini { margin: 8px 0; }
.otag { margin: 2px 4px 2px 0; }
.actions { display: flex; gap: 8px; border-top: 1px dashed #e8ecf0; padding-top: 10px; }
</style>
