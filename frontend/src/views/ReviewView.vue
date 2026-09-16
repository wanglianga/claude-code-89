<template>
  <div>
    <h2 class="page-title">社区复盘</h2>
    <div class="page-sub">按小区、回收员、公益项目和投诉类型复盘回收效果，让改进有数据依据</div>

    <el-row :gutter="14" class="section-card">
      <el-col :xs="12" :sm="8" :md="4" v-for="c in cards" :key="c.label">
        <div class="stat-card"><div class="num">{{ c.value }}</div><div class="label">{{ c.label }}</div></div>
      </el-col>
    </el-row>

    <el-card class="section-card">
      <div class="toolbar">
        <el-input v-model="communityFilter" placeholder="按小区筛选（留空=全部）" clearable style="width:240px" @change="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <div style="flex:1"></div>
        <el-radio-group v-model="dim" size="small">
          <el-radio-button label="community">按小区</el-radio-button>
          <el-radio-button label="collector">按回收员</el-radio-button>
          <el-radio-button label="project">按公益项目</el-radio-button>
          <el-radio-button label="aid">定向领取效果</el-radio-button>
          <el-radio-button label="complaint">按投诉类型</el-radio-button>
        </el-radio-group>
      </div>

      <div ref="chartEl" style="height:320px;margin:10px 0"></div>

      <el-table :data="rows" size="small" stripe>
        <template v-if="dim==='community'">
          <el-table-column prop="communityName" label="小区" min-width="150" />
          <el-table-column prop="orderCount" label="回收单数" width="100" />
          <el-table-column prop="weightKg" label="分拣重量kg" width="120" />
          <el-table-column prop="donatedCount" label="已捐赠" width="90" />
          <el-table-column prop="recycledCount" label="已再生" width="90" />
          <el-table-column prop="rejectedCount" label="拒收/终止" width="100" />
          <el-table-column prop="complaintCount" label="投诉数" width="90" />
        </template>
        <template v-if="dim==='collector'">
          <el-table-column prop="collectorName" label="回收员" min-width="140" />
          <el-table-column prop="orderCount" label="完成单量" width="100" />
          <el-table-column prop="weightKg" label="上门称重kg" width="130" />
          <el-table-column label="迟到次数" width="100">
            <template #default="{row}">
              <el-tag size="small" :type="row.lateCount>0?'danger':'success'">{{ row.lateCount }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="complaintCount" label="称重/迟到投诉" width="140" />
        </template>
        <template v-if="dim==='project'">
          <el-table-column prop="batchCode" label="批次" width="120" />
          <el-table-column prop="projectName" label="公益项目" min-width="170" />
          <el-table-column prop="orgName" label="公益机构" width="150" />
          <el-table-column prop="partnerName" label="合作方" width="140" />
          <el-table-column prop="totalWeightKg" label="重量kg" width="100" />
          <el-table-column prop="recycledWeightKg" label="再生量kg" width="100" />
          <el-table-column label="状态" width="110">
            <template #default="{row}">
              <el-tag size="small" :type="BATCH_STATUS[row.status]?.type">{{ BATCH_STATUS[row.status]?.label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="rejectReason" label="拒收原因" min-width="180" show-overflow-tooltip />
        </template>
        <template v-if="dim==='aid'">
          <el-table-column prop="batchCode" label="批次" width="120" />
          <el-table-column prop="projectName" label="公益项目" min-width="170" />
          <el-table-column prop="orgName" label="机构" width="150" />
          <el-table-column prop="designatedTarget" label="指定对象" width="130" />
          <el-table-column prop="familyCount" label="发放户数" width="90" />
          <el-table-column prop="quantity" label="发放件数" width="90" />
          <el-table-column prop="proxyCount" label="代领次数" width="90" />
          <el-table-column prop="visitCount" label="回访数" width="80" />
          <el-table-column label="平均满意度" width="110">
            <template #default="{row}">{{ row.avgSatisfaction == null ? '待回访' : row.avgSatisfaction + ' 分' }}</template>
          </el-table-column>
          <el-table-column label="后续需求" min-width="180">
            <template #default="{row}">{{ (row.followupNeeds || []).join('；') || '-' }}</template>
          </el-table-column>
        </template>
        <template v-if="dim==='complaint'">
          <el-table-column prop="typeLabel" label="投诉类型" min-width="140" />
          <el-table-column prop="total" label="总数" width="90" />
          <el-table-column prop="resolved" label="已解决" width="90" />
          <el-table-column prop="open" label="未结" width="90" />
          <el-table-column label="解决率" width="160">
            <template #default="{row}">
              <el-progress :percentage="row.total?Math.round(row.resolved/row.total*100):0" :stroke-width="12" />
            </template>
          </el-table-column>
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import api from '../api'
import { BATCH_STATUS } from '../store'

const dim = ref('community')
const communityFilter = ref('')
const overview = ref({})
const rows = ref([])
const chartEl = ref()
let chart

const cards = ref([])

async function loadOverview() {
  overview.value = await api.get('/api/stats/overview')
  const o = overview.value
  cards.value = [
    { label: '回收单总数', value: o.totalOrders },
    { label: '分拣总重量kg', value: o.sortWeightKg },
    { label: '已捐赠签收', value: o.donatedCount },
    { label: '已环保再生', value: o.recycledCount },
    { label: '拒收/终止', value: o.rejectedCount },
    { label: '未结投诉', value: o.openComplaints }
  ]
}

async function load() {
  const q = communityFilter.value ? `?community=${encodeURIComponent(communityFilter.value)}` : ''
  const url = {
    community: '/api/stats/by-community',
    collector: '/api/stats/by-collector',
    project: '/api/stats/by-project',
    aid: '/api/stats/by-aid',
    complaint: '/api/stats/by-complaint'
  }[dim.value]
  rows.value = await api.get(url + (dim.value !== 'project' && dim.value !== 'aid' ? q : ''))
  await nextTick()
  renderChart()
}

function renderChart() {
  if (!chart) chart = echarts.init(chartEl.value)
  let option
  const d = rows.value
  if (dim.value === 'community') {
    option = {
      tooltip: { trigger: 'axis' },
      legend: { data: ['回收单数', '重量kg', '投诉数'], top: 0 },
      grid: { top: 40, bottom: 30, left: 50, right: 20 },
      xAxis: { type: 'category', data: d.map(r => r.communityName), axisLabel: { interval: 0 } },
      yAxis: [{ type: 'value', name: '单/件' }, { type: 'value', name: 'kg/投诉' }],
      series: [
        { name: '回收单数', type: 'bar', data: d.map(r => r.orderCount), itemStyle: { color: '#2f8f6b' } },
        { name: '重量kg', type: 'line', yAxisIndex: 1, data: d.map(r => Number(r.weightKg)), itemStyle: { color: '#5b8ff9' } },
        { name: '投诉数', type: 'bar', yAxisIndex: 1, data: d.map(r => r.complaintCount), itemStyle: { color: '#e6a23c' } }
      ]
    }
  } else if (dim.value === 'collector') {
    option = {
      tooltip: { trigger: 'axis' },
      legend: { data: ['单量', '迟到', '相关投诉'], top: 0 },
      grid: { top: 40, bottom: 30, left: 50, right: 20 },
      xAxis: { type: 'category', data: d.map(r => r.collectorName) },
      yAxis: { type: 'value' },
      series: [
        { name: '单量', type: 'bar', data: d.map(r => r.orderCount), itemStyle: { color: '#2f8f6b' } },
        { name: '迟到', type: 'bar', data: d.map(r => r.lateCount), itemStyle: { color: '#c45656' } },
        { name: '相关投诉', type: 'bar', data: d.map(r => r.complaintCount), itemStyle: { color: '#e6a23c' } }
      ]
    }
  } else if (dim.value === 'project') {
    option = {
      tooltip: { trigger: 'axis' },
      legend: { data: ['总重量kg', '再生量kg'], top: 0 },
      grid: { top: 40, bottom: 60, left: 50, right: 20 },
      xAxis: { type: 'category', data: d.map(r => r.batchCode + ' ' + (r.projectName || r.recyclerName || '')),
               axisLabel: { interval: 0, rotate: 18, fontSize: 10 } },
      yAxis: { type: 'value', name: 'kg' },
      series: [
        { name: '总重量kg', type: 'bar', data: d.map(r => Number(r.totalWeightKg)), itemStyle: { color: '#2f8f6b' } },
        { name: '再生量kg', type: 'bar', data: d.map(r => Number(r.recycledWeightKg || 0)), itemStyle: { color: '#5b8ff9' } }
      ]
    }
  } else if (dim.value === 'aid') {
    option = {
      tooltip: { trigger: 'axis' },
      legend: { data: ['发放件数', '回访数'], top: 0 },
      grid: { top: 40, bottom: 60, left: 50, right: 20 },
      xAxis: { type: 'category', data: d.map(r => r.projectName || r.batchCode),
               axisLabel: { interval: 0, rotate: 18, fontSize: 10 } },
      yAxis: { type: 'value' },
      series: [
        { name: '发放件数', type: 'bar', data: d.map(r => r.quantity), itemStyle: { color: '#2f8f6b' } },
        { name: '回访数', type: 'bar', data: d.map(r => r.visitCount), itemStyle: { color: '#f0b350' } }
      ]
    }
  } else {
    option = {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie', radius: ['40%', '68%'], center: ['50%', '45%'],
        label: { formatter: '{b}\n{c}件' },
        data: d.map((r, i) => ({
          name: r.typeLabel, value: r.total,
          itemStyle: { color: ['#c45656', '#e6a23c', '#f0b350', '#9a8c98', '#5b8ff9', '#2f8f6b'][i % 6] }
        }))
      }]
    }
  }
  chart.setOption(option, true)
}

watch(dim, load)
onMounted(async () => {
  await loadOverview()
  await load()
  window.addEventListener('resize', () => chart?.resize())
})
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 6px; }
</style>
