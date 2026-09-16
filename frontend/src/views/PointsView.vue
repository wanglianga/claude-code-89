<template>
  <div>
    <h2 class="page-title">环保积分</h2>
    <div class="page-sub">回收按重量累计积分，可兑换环保商品；积分财务负责发放、补发与对账</div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="resident ? 14 : 24">
        <!-- 居民：积分商城 -->
        <el-card v-if="resident" class="section-card brand-gradient mall-head">
          <div>
            <div style="opacity:.9">我的积分余额</div>
            <div class="balance">{{ balance }}</div>
          </div>
          <div style="font-size:12px;opacity:.85;align-self:flex-end">
            积分规则：纯积分 50分/kg · 积分+捐赠 25分/kg · 公益捐赠活动奖励 100分
          </div>
        </el-card>

        <el-card v-if="resident" class="section-card">
          <template #header><b>环保积分商城</b></template>
          <el-row :gutter="14">
            <el-col :xs="12" :sm="8" v-for="p in products" :key="p.id">
              <div class="product">
                <div class="p-icon">{{ p.icon }}</div>
                <div class="p-name">{{ p.name }}</div>
                <div class="p-desc">{{ p.description }}</div>
                <div class="p-foot">
                  <b>{{ p.pointsCost }}</b> 分
                  <el-button size="small" type="primary" round :disabled="p.stock<=0 || balance<p.pointsCost"
                             @click="exchange(p)">兑换</el-button>
                </div>
                <div class="p-stock">库存 {{ p.stock }}</div>
              </div>
            </el-col>
          </el-row>
        </el-card>

        <!-- 财务：待发放兑换单 -->
        <el-card v-if="finance" class="section-card">
          <template #header>
            <div class="card-head">
              <b>环保商品兑换单</b>
              <el-radio-group v-model="exFilter" size="small">
                <el-radio-button label="ALL">全部</el-radio-button>
                <el-radio-button label="ORDERED">待发放</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-table :data="filteredExchanges" size="small">
            <el-table-column label="居民" prop="residentName" width="110" />
            <el-table-column label="商品"><template #default="{row}">{{ row.product.icon }} {{ row.product.name }} ×{{ row.quantity }}</template></el-table-column>
            <el-table-column label="消耗积分" prop="totalPoints" width="100" />
            <el-table-column label="状态" width="100">
              <template #default="{row}">
                <el-tag size="small" :type="row.status==='DELIVERED'?'success':'warning'">
                  {{ row.status==='DELIVERED'?'已发放':'待发放' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{row}">
                <el-button v-if="row.status==='ORDERED'" link type="primary" size="small" @click="deliver(row)">确认发放</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 财务：手工补发 -->
        <el-card v-if="finance" class="section-card">
          <template #header><b>积分手工调整 / 投诉补发</b></template>
          <el-form inline>
            <el-form-item label="居民账户">
              <el-select v-model="adjust.residentId" filterable placeholder="选择居民" style="width:200px">
                <el-option v-for="r in residentAccounts" :key="r.id"
                           :label="`${r.displayName}（@${r.username}，余额 ${r.pointsBalance}）`" :value="r.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="积分变动">
              <el-input-number v-model="adjust.points" :step="50" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="adjust.remark" placeholder="补发依据" style="width:220px" />
            </el-form-item>
            <el-button type="warning" @click="submitAdjust">提交调整</el-button>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="resident ? 10 : 24">
        <el-card class="section-card">
          <template #header>
            <div class="card-head">
              <b>积分流水</b>
              <el-select v-if="finance" v-model="ledgerResidentId" clearable filterable placeholder="全部居民"
                         size="small" style="width:200px" @change="loadLedger">
                <el-option v-for="r in residentAccounts" :key="r.id" :label="r.displayName" :value="r.id" />
              </el-select>
            </div>
          </template>
          <el-table :data="ledger" size="small" max-height="560">
            <el-table-column label="类型" width="110">
              <template #default="{row}">
                <el-tag size="small" :type="ledgerMeta[row.type].type">{{ ledgerMeta[row.type].label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="变动" width="90">
              <template #default="{row}">
                <span :style="{color: row.points>=0 ? '#2f8f6b' : '#e6a23c', fontWeight:700}">
                  {{ row.points>0 ? '+' : '' }}{{ row.points }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="余额" prop="balanceAfter" width="80" />
            <el-table-column label="说明" prop="remark" min-width="180" show-overflow-tooltip />
            <el-table-column label="时间" width="130"><template #default="{row}">{{ fmt(row.createdAt) }}</template></el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useAuth } from '../store'

const auth = useAuth()
const resident = computed(() => auth.role === 'RESIDENT')
const finance = computed(() => ['FINANCE', 'ADMIN'].includes(auth.role))

const balance = ref(0)
const products = ref([])
const ledger = ref([])
const exchanges = ref([])
const residentAccounts = ref([])
const exFilter = ref('ALL')
const ledgerResidentId = ref(null)
const adjust = ref({ residentId: null, points: 50, remark: '' })

const ledgerMeta = {
  EARN: { label: '回收获取', type: 'success' },
  SPEND: { label: '兑换消耗', type: 'warning' },
  ADJUST: { label: '财务补发', type: 'danger' },
  CAMPAIGN_BONUS: { label: '活动奖励', type: 'primary' }
}

const filteredExchanges = computed(() =>
  exFilter.value === 'ALL' ? exchanges.value : exchanges.value.filter(e => e.status === exFilter.value))

async function loadLedger() {
  const q = ledgerResidentId.value ? `?residentId=${ledgerResidentId.value}` : ''
  ledger.value = await api.get('/api/points/ledger' + q)
}

onMounted(async () => {
  if (resident.value) {
    balance.value = (await api.get('/api/points/balance')).balance
    products.value = await api.get('/api/points/products')
  }
  exchanges.value = await api.get('/api/points/exchanges')
  await loadLedger()
  if (finance.value) residentAccounts.value = await api.get('/api/points/residents')
})

async function exchange(p) {
  await ElMessageBox.confirm(`使用 ${p.pointsCost} 积分兑换「${p.name}」？`, '兑换确认', { type: 'warning' })
  await api.post('/api/points/exchange', { productId: p.id, quantity: 1 })
  ElMessage.success('兑换成功，等待积分财务发放')
  balance.value = (await api.get('/api/points/balance')).balance
  exchanges.value = await api.get('/api/points/exchanges')
  loadLedger()
}

async function deliver(row) {
  await api.post(`/api/points/exchanges/${row.id}/deliver`, {})
  ElMessage.success('已发放')
  exchanges.value = await api.get('/api/points/exchanges')
}

async function submitAdjust() {
  if (!adjust.value.residentId) return ElMessage.warning('请选择居民')
  await api.post('/api/points/adjust', adjust.value)
  ElMessage.success('积分调整成功')
  adjust.value = { residentId: null, points: 50, remark: '' }
  await loadLedger()
  residentAccounts.value = await api.get('/api/points/residents')
}

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '' }
</script>

<style scoped>
.mall-head { display: flex; justify-content: space-between; border: none; }
:deep(.el-card.mall-head) { background: linear-gradient(120deg, #1f6f54, #47a084); }
.balance { font-size: 40px; font-weight: 800; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.product { border: 1px solid #edf0f2; border-radius: 12px; padding: 16px; text-align: center; margin-bottom: 14px; }
.p-icon { font-size: 40px; }
.p-name { font-weight: 700; margin: 6px 0 2px; }
.p-desc { color: #97a0af; font-size: 12px; height: 32px; }
.p-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
.p-stock { color: #c0c6cf; font-size: 11px; margin-top: 4px; }
</style>
