<template>
  <el-container v-if="auth.isLogin" style="height:100vh">
    <el-aside width="232px" class="aside">
      <div class="brand">
        <span class="logo">♻️</span>
        <div>
          <div class="name">绿衣循环</div>
          <div class="slogan">旧衣回收协同平台</div>
        </div>
      </div>
      <el-menu :default-active="route.path" router background-color="transparent"
               text-color="#cfe3d9" active-text-color="#ffffff" class="menu">
        <el-menu-item v-for="m in menus" :key="m.index" :index="m.index">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
        <el-menu-item index="public-link" @click="goPublic">
          <el-icon><View /></el-icon><span>公益公示端</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="crumb">
          <span class="title">{{ route.meta.title }}</span>
          <el-tag size="small" effect="dark" type="success" round>{{ auth.roleLabel }}</el-tag>
        </div>
        <div class="user">
          <el-avatar :size="32" class="ava">{{ auth.user?.displayName?.[0] || '用' }}</el-avatar>
          <div class="who">
            <div class="dn">{{ auth.user?.displayName }}</div>
            <div class="un">@{{ auth.user?.username }}</div>
          </div>
          <el-button text :icon="SwitchButton" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Odometer, List, Box, ChatDotRound, GoldMedal, OfficeBuilding, DataAnalysis,
  View, SwitchButton, Connection
} from '@element-plus/icons-vue'
import { useAuth } from '../store'

const auth = useAuth()
const route = useRoute()
const router = useRouter()

const ALL = ['RESIDENT', 'COLLECTOR', 'SORTER', 'COMMUNITY', 'ORG', 'FINANCE', 'ADMIN']
const items = [
  { index: '/app/dashboard', icon: Odometer, title: '工作台', roles: ALL },
  { index: '/app/orders', icon: List, title: '回收单', roles: ALL },
  { index: '/app/batches', icon: Box, title: '批次与去向', roles: ['SORTER', 'COMMUNITY', 'ORG', 'ADMIN'] },
  { index: '/app/complaints', icon: ChatDotRound, title: '投诉协同', roles: ALL },
  { index: '/app/points', icon: GoldMedal, title: '环保积分', roles: ['RESIDENT', 'FINANCE', 'ADMIN'] },
  { index: '/app/partners', icon: OfficeBuilding, title: '合作与活动', roles: ['COMMUNITY', 'ORG', 'ADMIN'] },
  { index: '/app/aid', icon: Connection, title: '定向领取', roles: ['COMMUNITY', 'SORTER', 'ORG', 'FINANCE', 'ADMIN'] },
  { index: '/app/review', icon: DataAnalysis, title: '社区复盘', roles: ['COMMUNITY', 'FINANCE', 'ADMIN'] }
]
const menus = computed(() => items.filter(m => m.roles.includes(auth.role)))

if (!auth.isLogin) router.replace('/login')

function goPublic() { location.hash = '#/public' }
function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.aside {
  background: linear-gradient(180deg, #1f6f54 0%, #237053 100%);
  display: flex;
  flex-direction: column;
}
.brand { display: flex; align-items: center; gap: 10px; padding: 20px 18px 16px; color: #fff; }
.brand .logo { font-size: 30px; }
.brand .name { font-weight: 800; font-size: 17px; }
.brand .slogan { font-size: 11px; opacity: .8; }
.menu { border-right: none; flex: 1; padding: 0 10px; }
.menu .el-menu-item { border-radius: 8px; margin: 4px 0; height: 46px; }
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #edf0f2;
}
.crumb { display: flex; align-items: center; gap: 10px; }
.crumb .title { font-size: 17px; font-weight: 700; }
.user { display: flex; align-items: center; gap: 10px; }
.ava { background: var(--brand); color: #fff; font-weight: 700; }
.who .dn { font-size: 13px; font-weight: 600; line-height: 1.2; }
.who .un { font-size: 11px; color: #97a0af; }
.main { background: #f4f6f8; padding: 22px; overflow-y: auto; }
</style>
