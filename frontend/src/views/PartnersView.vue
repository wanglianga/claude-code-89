<template>
  <div>
    <h2 class="page-title">合作与帮扶</h2>
    <div class="page-sub">企业公益合作 · 学校捐衣活动 · 低收入家庭定向领取（家庭信息脱敏存储与公示）</div>

    <el-tabs v-model="tab">
      <!-- ============ 合作活动 ============ -->
      <el-tab-pane label="企业公益合作 / 学校捐衣活动" name="partners">
        <el-card>
          <div class="toolbar">
            <el-radio-group v-model="pType" size="small" @change="loadPartners">
              <el-radio-button label="">全部</el-radio-button>
              <el-radio-button label="ENTERPRISE">企业合作</el-radio-button>
              <el-radio-button label="SCHOOL">学校活动</el-radio-button>
            </el-radio-group>
            <div style="flex:1"></div>
            <el-button v-if="canManage" type="primary" :icon="Plus" @click="openPartner">新建合作活动</el-button>
          </div>
          <el-row :gutter="14">
            <el-col :xs="24" :md="12" v-for="p in partners" :key="p.id">
              <el-card class="p-card">
                <div class="pc-head">
                  <el-tag :type="p.type==='SCHOOL'?'warning':'primary'" size="small" effect="dark">
                    {{ p.type==='SCHOOL' ? '🏫 学校捐衣活动' : '🏢 企业公益合作' }}
                  </el-tag>
                  <el-tag size="small" :type="p.status==='ACTIVE'?'success':'info'">
                    {{ p.status==='ACTIVE' ? '进行中' : '已结束' }}
                  </el-tag>
                </div>
                <div class="pc-name">{{ p.name }}</div>
                <div class="pc-proj">🎯 {{ p.projectName }}</div>
                <div class="pc-desc muted">{{ p.description }}</div>
                <el-progress :percentage="progress(p)" :stroke-width="14"
                             :status="progress(p)>=100?'success':''" />
                <div class="pc-nums">
                  已募集 <b>{{ p.collectedKg }}</b> / 目标 {{ p.targetKg }} kg
                  · {{ p.startDate }} ~ {{ p.endDate }}
                </div>
                <div class="muted">联系人：{{ p.contactName }} {{ p.contactPhone }}</div>
              </el-card>
            </el-col>
          </el-row>
        </el-card>
      </el-tab-pane>

      <!-- ============ 低收入家庭定向领取 ============ -->
      <el-tab-pane label="低收入家庭定向领取" name="aid">
        <el-card>
          <div class="toolbar">
            <el-alert type="info" :closable="false" style="flex:1"
                      title="家庭姓名、电话仅以脱敏形式登记与展示；衣物来源批次、领取凭证与经办人均留痕可溯" />
            <el-button v-if="canAid" type="primary" :icon="Plus" @click="openAid">登记困难家庭</el-button>
          </div>
          <el-table :data="families" size="small" stripe style="margin-top:12px">
            <el-table-column prop="maskedName" label="姓名(脱敏)" width="110" />
            <el-table-column prop="maskedPhone" label="电话(脱敏)" width="130" />
            <el-table-column prop="communityName" label="社区/小区" width="150" />
            <el-table-column prop="familySize" label="家庭人数" width="90" />
            <el-table-column prop="needNote" label="困难情况（社区核实）" min-width="200" show-overflow-tooltip />
            <el-table-column prop="voucherNo" label="领取凭证" width="150" />
            <el-table-column label="状态" width="100">
              <template #default="{row}">
                <el-tag size="small" :type="row.status==='RECEIVED'?'success':'warning'">
                  {{ row.status==='RECEIVED' ? '已领取' : '待领取' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="来源批次/备注" min-width="180">
              <template #default="{row}">
                <div v-if="row.batchCode">批次 {{ row.batchCode }} · {{ row.remark }}</div>
                <span v-else class="muted">尚未发放</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{row}">
                <el-button v-if="canAid && row.status==='RESERVED'" link type="primary" size="small" @click="openDeliver(row)">
                  定向发放
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建活动 -->
    <el-dialog v-model="pDlg" title="新建合作活动" width="520px">
      <el-form :model="pf" label-width="100px">
        <el-form-item label="类型">
          <el-radio-group v-model="pf.type">
            <el-radio value="ENTERPRISE">企业公益合作</el-radio>
            <el-radio value="SCHOOL">学校捐衣活动</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="pf.name" :placeholder="pf.type==='SCHOOL'?'如：阳光实验小学':'如：华远地产集团'" /></el-form-item>
        <el-form-item label="项目名称"><el-input v-model="pf.projectName" placeholder="如：童心捐衣季" /></el-form-item>
        <el-form-item label="联系人/电话">
          <div style="display:flex;gap:8px">
            <el-input v-model="pf.contactName" placeholder="联系人" />
            <el-input v-model="pf.contactPhone" placeholder="电话" />
          </div>
        </el-form-item>
        <el-form-item label="活动周期">
          <div style="display:flex;gap:8px;align-items:center">
            <el-date-picker v-model="pf.range" type="daterange" value-format="YYYY-MM-DD"
                            start-placeholder="开始" end-placeholder="结束" />
          </div>
        </el-form-item>
        <el-form-item label="目标重量kg"><el-input-number v-model="pf.targetKg" :min="1" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="pf.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pDlg=false">取消</el-button>
        <el-button type="primary" @click="submitPartner">创建</el-button>
      </template>
    </el-dialog>

    <!-- 登记家庭 -->
    <el-dialog v-model="aDlg" title="登记低收入家庭（脱敏）" width="500px">
      <el-form :model="af" label-width="100px">
        <el-form-item label="脱敏姓名"><el-input v-model="af.maskedName" placeholder="如：张*" /></el-form-item>
        <el-form-item label="脱敏电话"><el-input v-model="af.maskedPhone" placeholder="如：138****2103" /></el-form-item>
        <el-form-item label="小区"><el-input v-model="af.communityName" /></el-form-item>
        <el-form-item label="家庭人数"><el-input-number v-model="af.familySize" :min="1" /></el-form-item>
        <el-form-item label="困难情况"><el-input v-model="af.needNote" type="textarea" :rows="3" placeholder="社区核实情况" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aDlg=false">取消</el-button>
        <el-button type="primary" @click="submitAid">登记并生成凭证</el-button>
      </template>
    </el-dialog>

    <!-- 定向发放 -->
    <el-dialog v-model="dDlg" title="从已签收捐赠批次定向发放" width="480px">
      <el-form label-width="100px">
        <el-form-item label="选择批次">
          <el-select v-model="df.batchId" style="width:100%" placeholder="公益机构已签收批次">
            <el-option v-for="b in receivedBatches" :key="b.id"
                       :label="`${b.code} · ${b.projectName} · ${b.totalWeightKg}kg`" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发放备注"><el-input v-model="df.remark" type="textarea" :rows="2"
          placeholder="如：发放冬装6件，家属/经办人签字确认" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dDlg=false">取消</el-button>
        <el-button type="primary" @click="submitDeliver">确认发放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import api from '../api'
import { useAuth } from '../store'

const auth = useAuth()
const tab = ref('partners')
const canManage = computed(() => ['COMMUNITY', 'ADMIN'].includes(auth.role))
const canAid = computed(() => ['COMMUNITY', 'ADMIN'].includes(auth.role))

const partners = ref([])
const families = ref([])
const receivedBatches = ref([])
const pType = ref('')

async function loadPartners() {
  partners.value = await api.get('/api/partners' + (pType.value ? `?type=${pType.value}` : ''))
}
function progress(p) {
  if (!p.targetKg || Number(p.targetKg) === 0) return 0
  return Math.min(100, Math.round(Number(p.collectedKg) / Number(p.targetKg) * 100))
}

onMounted(async () => {
  await loadPartners()
  try { families.value = await api.get('/api/aid-families') } catch (e) { families.value = [] }
  const batches = await api.get('/api/batches')
  receivedBatches.value = batches.filter(b => ['RECEIVED', 'AID_GIVEN'].includes(b.status) && b.batchType === 'DONATION')
})

// 活动
const pDlg = ref(false)
const pf = ref({})
function openPartner() {
  pf.value = { type: 'ENTERPRISE', name: '', projectName: '', contactName: '', contactPhone: '',
    range: [], targetKg: 100, description: '' }
  pDlg.value = true
}
async function submitPartner() {
  if (!pf.value.name || !pf.value.projectName) return ElMessage.warning('名称与项目名必填')
  await api.post('/api/partners', {
    ...pf.value, startDate: pf.value.range?.[0], endDate: pf.value.range?.[1]
  })
  ElMessage.success('活动已创建，居民预约时可选择参加')
  pDlg.value = false
  loadPartners()
}

// 家庭
const aDlg = ref(false)
const af = ref({})
function openAid() {
  af.value = { maskedName: '', maskedPhone: '', communityName: auth.user.communityName || '', familySize: 1, needNote: '' }
  aDlg.value = true
}
async function submitAid() {
  if (!af.value.maskedName) return ElMessage.warning('请填写脱敏姓名')
  await api.post('/api/aid-families', af.value)
  ElMessage.success('已登记并生成领取凭证')
  aDlg.value = false
  families.value = await api.get('/api/aid-families')
}

// 发放
const dDlg = ref(false)
const df = ref({})
let aidTarget = null
function openDeliver(row) {
  aidTarget = row
  df.value = { batchId: null, remark: '' }
  dDlg.value = true
}
async function submitDeliver() {
  if (!df.value.batchId) return ElMessage.warning('请选择已签收批次')
  await api.post(`/api/aid-families/${aidTarget.id}/deliver`, df.value)
  ElMessage.success('定向发放完成，公示端将同步显示去向')
  dDlg.value = false
  families.value = await api.get('/api/aid-families')
}
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; align-items: center; }
.p-card { margin-bottom: 14px; }
.pc-head { display: flex; justify-content: space-between; margin-bottom: 8px; }
.pc-name { font-size: 16px; font-weight: 700; }
.pc-proj { margin: 4px 0; font-weight: 600; color: var(--brand-dark); }
.pc-desc { font-size: 12px; margin-bottom: 10px; min-height: 32px; }
.pc-nums { font-size: 12px; color: #7a869a; margin: 8px 0 4px; }
.pc-nums b { color: var(--brand); font-size: 14px; }
.muted { color: #97a0af; font-size: 12px; }
</style>
