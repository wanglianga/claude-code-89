<template>
  <div class="public-page">
    <header class="topbar">
      <div class="inner top-inner">
        <div class="pbrand">♻️ 绿衣循环 · 公益去向公示</div>
        <div>
          <el-button round @click="location.hash = '#/login'">业务端登录</el-button>
        </div>
      </div>
    </header>

    <section class="hero">
      <div class="inner">
        <h1>每一件旧衣，去向都看得见</h1>
        <p>批次发运 · 公益机构签收 · 定向发放 · 环保再生处理量，全流程照片公示，接受居民监督</p>
        <el-row :gutter="16" class="kpis">
          <el-col :xs="12" :sm="8" :md="4" v-for="k in kpis" :key="k.label">
            <div class="kpi">
              <div class="n">{{ k.value }}</div>
              <div class="l">{{ k.label }}</div>
            </div>
          </el-col>
        </el-row>
      </div>
    </section>

    <div class="inner content">
      <el-row :gutter="16">
        <el-col :xs="24" :md="10">
          <el-card class="section-card">
            <template #header><b>分拣五分类去向（重量 kg）</b></template>
            <div ref="pieEl" style="height:280px"></div>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="14">
          <el-card class="section-card">
            <template #header><b>透明公示承诺</b></template>
            <el-steps direction="vertical" :active="5" finish-status="success">
              <el-step title="居民预约" description="记录数量、品类、清洗情况、鞋包被褥、地址时段与捐赠意愿" />
              <el-step title="上门称重拍照" description="回收员现场称重、拍照、初步分类，居民确认积分或捐赠" />
              <el-step title="分拣中心复核" description="五分类 + 重量差异 + 污损原因；校服/工作服/信息衣物脱敏或拒收" />
              <el-step title="批次发运" description="同去向集货，上传打包/装车捐赠照片" />
              <el-step title="机构签收 / 再生登记" description="公益机构签收照片与签收人留痕；再生批次公示处理量与处置证明" />
            </el-steps>
          </el-card>
        </el-col>
      </el-row>

      <div class="list-head">
        <h3>批次去向公示（{{ batches.length }}）</h3>
        <el-radio-group v-model="filter" size="small">
          <el-radio-button label="ALL">全部</el-radio-button>
          <el-radio-button label="DONATION">公益捐赠</el-radio-button>
          <el-radio-button label="RECYCLE">环保再生</el-radio-button>
        </el-radio-group>
      </div>

      <el-row :gutter="16">
        <el-col :xs="24" :md="12" v-for="b in shownBatches" :key="b.id">
          <el-card class="batch-card">
            <div class="bc-head">
              <div>
                <el-tag :type="b.batchType === 'RECYCLE' ? 'primary' : 'success'" size="small" effect="dark">
                  {{ b.batchType === 'RECYCLE' ? '环保再生' : '公益捐赠' }}
                </el-tag>
                <b class="bc-code">{{ b.code }}</b>
              </div>
              <el-tag :type="BATCH_STATUS[b.status]?.type" size="small">{{ BATCH_STATUS[b.status]?.label }}</el-tag>
            </div>

            <div class="bc-dest">
              {{ b.batchType === 'RECYCLE'
                ? ('再生处理厂：' + (b.recyclerName || '-'))
                : ('公益项目：' + (b.projectName || '-') + '（' + (b.organization?.organizationName || '-') + '）') }}
            </div>
            <div v-if="b.partner" class="bc-partner">
              🏷️ {{ b.partner.type === 'SCHOOL' ? '学校捐衣活动' : '企业公益合作' }} · {{ b.partner.name }} · {{ b.partner.projectName }}
            </div>

            <el-row :gutter="8" class="bc-nums">
              <el-col :span="8"><div class="bn">{{ b.totalWeightKg }}<span>kg</span></div><div class="bl">总重量</div></el-col>
              <el-col :span="8"><div class="bn">{{ b.itemCount }}<span>件</span></div><div class="bl">衣物数量</div></el-col>
              <el-col :span="8">
                <div class="bn" v-if="b.batchType === 'RECYCLE'">{{ b.recycledWeightKg || '-' }}<span>kg</span></div>
                <div class="bn" v-else>{{ b.orderCount }}<span>单</span></div>
                <div class="bl">{{ b.batchType === 'RECYCLE' ? '再生处理量' : '含回收单' }}</div>
              </el-col>
            </el-row>

            <el-row :gutter="8" v-if="b.donationPhotoUrl || b.signPhotoUrl">
              <el-col :span="12" v-if="b.donationPhotoUrl">
                <el-image :src="b.donationPhotoUrl" fit="cover" class="ph">
                  <template #placeholder><div class="ph-tip">捐赠/装车照片</div></template>
                </el-image>
                <div class="ph-cap">捐赠/装车照片</div>
              </el-col>
              <el-col :span="12" v-if="b.signPhotoUrl">
                <el-image :src="b.signPhotoUrl" fit="cover" class="ph">
                  <template #placeholder><div class="ph-tip">机构签收照片</div></template>
                </el-image>
                <div class="ph-cap">机构签收照片</div>
              </el-col>
            </el-row>

            <el-descriptions :column="1" size="small" border class="bc-desc">
              <el-descriptions-item v-if="b.receiverName" label="签收人">{{ b.receiverName }}</el-descriptions-item>
              <el-descriptions-item v-if="b.signNote" label="签收备注">{{ b.signNote }}</el-descriptions-item>
              <el-descriptions-item v-if="b.status === 'AID_GIVEN'" label="定向发放">
                已发放至经社区核实的低收入家庭（信息脱敏）
              </el-descriptions-item>
              <el-descriptions-item v-if="b.rejectReason" label="拒收原因">
                <span style="color:#c45656">{{ b.rejectReason }}</span>
              </el-descriptions-item>
              <el-descriptions-item v-if="b.publicNote" label="公示说明">{{ b.publicNote }}</el-descriptions-item>
            </el-descriptions>

            <el-collapse>
              <el-collapse-item :title="`本批 ${b.orders.length} 张回收单溯源`" :name="1">
                <div v-for="o in b.orders" :key="o.id" class="trace">
                  <el-link type="info" class="trace-code">{{ o.code }}</el-link>
                  <el-tag size="small">{{ o.communityName }}</el-tag>
                  <el-tag size="small" :type="SORT_CATEGORY[o.category]?.type" effect="plain">
                    {{ SORT_CATEGORY[o.category]?.label }}
                  </el-tag>
                  <el-tag v-if="o.privacy" size="small" type="success" effect="dark">
                    🔒 {{ o.privacy.label }}
                  </el-tag>
                  <span class="trace-w">{{ o.weightKg }}kg</span>
                </div>
                <div v-if="b.orders.some(o => o.privacy)" class="privacy-note">
                  🔒 含个人标识的校服/工作服已按隐私规范处置：脱敏件拆除标识并复检留证，拒收件未进入公益流转。
                  <template v-for="o in b.orders.filter(x => x.privacy?.evidencePhotoUrl)" :key="o.id">
                    <el-image class="privacy-ev" :src="o.privacy.evidencePhotoUrl"
                              :preview-src-list="[o.privacy.evidencePhotoUrl]"
                              fit="cover" preview-teleported />
                  </template>
                </div>
              </el-collapse-item>
            </el-collapse>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!shownBatches.length" description="暂无已公示批次" />
    </div>
    <footer class="footer">绿衣循环平台 · 数据来自各角色在同一回收单中的真实留痕 · 接受全社会监督</footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import api from '../api'
import { BATCH_STATUS, SORT_CATEGORY } from '../store'

const stats = ref({ categoryWeightKg: {} })
const batches = ref([])
const filter = ref('ALL')
const pieEl = ref()
const location = window.location

const CAT_META = {
  DIRECT_DONATE: { label: '可直接捐赠', color: '#67c28a' },
  NEED_CLEAN: { label: '需消毒整理', color: '#f0b350' },
  ECO_RECYCLE: { label: '环保再生', color: '#5b8ff9' },
  NON_RECYCLABLE: { label: '不可回收', color: '#c45656' },
  SPECIAL: { label: '特殊处理', color: '#9a8c98' }
}

const kpis = computed(() => [
  { label: '累计回收单', value: stats.value.orderCount ?? '-' },
  { label: '公益捐赠(kg)', value: stats.value.donatedWeightKg ?? 0 },
  { label: '再生处理(kg)', value: stats.value.recycledWeightKg ?? 0 },
  { label: '定向帮扶(kg)', value: stats.value.aidWeightKg ?? 0 },
  { label: '公益机构', value: stats.value.orgCount ?? 0 },
  { label: '合作企业/学校', value: `${stats.value.enterpriseCount ?? 0}/${stats.value.schoolCount ?? 0}` }
])

const shownBatches = computed(() =>
  filter.value === 'ALL' ? batches.value : batches.value.filter(b => b.batchType === filter.value))

function renderPie() {
  const data = Object.entries(stats.value.categoryWeightKg || {})
    .map(([k, v]) => ({ name: CAT_META[k].label, value: v || 0, itemStyle: { color: CAT_META[k].color } }))
  const chart = echarts.init(pieEl.value)
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}kg ({d}%)' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 12, textStyle: { fontSize: 12 } },
    series: [{
      type: 'pie', radius: ['42%', '68%'], center: ['50%', '44%'],
      label: { formatter: '{b}\n{c}kg', fontSize: 11 },
      data
    }]
  })
  window.addEventListener('resize', () => chart.resize())
}

onMounted(async () => {
  const [s, b] = await Promise.all([
    api.get('/api/public/stats'),
    api.get('/api/public/batches')
  ])
  stats.value = s
  batches.value = b
  await nextTick()
  renderPie()
})
</script>

<style scoped>
.public-page { min-height: 100vh; background: #f4f6f8; }
.inner { max-width: 1180px; margin: 0 auto; padding: 0 20px; }
.topbar { background: #fff; box-shadow: 0 1px 6px rgba(0,0,0,.06); position: sticky; top: 0; z-index: 10; }
.top-inner { display: flex; align-items: center; justify-content: space-between; height: 60px; }
.pbrand { font-weight: 800; font-size: 17px; color: var(--brand-dark); }
.hero {
  background: linear-gradient(120deg, #1f6f54, #3d9970 55%, #6fbf95);
  color: #fff; padding: 46px 0 60px;
}
.hero h1 { font-size: 30px; margin: 0 0 10px; }
.hero p { opacity: .92; margin: 0 0 26px; }
.kpis .kpi {
  background: rgba(255,255,255,.14); backdrop-filter: blur(4px);
  border: 1px solid rgba(255,255,255,.25);
  border-radius: 12px; padding: 14px; text-align: center; margin-bottom: 12px;
}
.kpi .n { font-size: 24px; font-weight: 800; }
.kpi .l { font-size: 12px; opacity: .85; margin-top: 2px; }
.content { padding-top: 22px; }
.list-head { display: flex; justify-content: space-between; align-items: center; margin: 8px 0 14px; }
.list-head h3 { margin: 0; }
.batch-card { margin-bottom: 16px; }
.bc-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.bc-code { margin-left: 8px; font-size: 15px; }
.bc-dest { font-weight: 600; margin: 4px 0; }
.bc-partner { color: #b07d2b; font-size: 12px; margin-bottom: 8px; }
.bc-nums { text-align: center; margin: 8px 0; }
.bn { font-size: 20px; font-weight: 800; color: var(--brand); }
.bn span { font-size: 12px; font-weight: 400; color: #97a0af; margin-left: 2px; }
.bl { font-size: 12px; color: #97a0af; }
.ph { width: 100%; height: 150px; border-radius: 8px; background: #eef2f0; display:block; }
.ph-tip { width: 100%; height: 150px; display:flex; align-items:center; justify-content:center; color:#97a0af; font-size:12px; }
.ph-cap { font-size: 12px; color: #97a0af; text-align: center; margin: 4px 0 8px; }
.bc-desc { margin-top: 8px; }
.trace { display: flex; align-items: center; gap: 8px; padding: 4px 0; font-size: 12px; }
.trace-code { font-weight: 600; }
.trace-w { color: #64705a; margin-left: auto; }
.privacy-note { background: #f0f7f4; border: 1px solid #cde7dc; color: #3a7a5f; border-radius: 8px;
  padding: 8px 10px; font-size: 12px; margin: 6px 0; display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.privacy-ev { width: 54px; height: 40px; border-radius: 6px; cursor: zoom-in; }
.footer { text-align: center; color: #97a0af; font-size: 12px; padding: 30px 0; }
</style>
