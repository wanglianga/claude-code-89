<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-left brand-gradient">
        <div class="logo">♻️ 绿衣循环</div>
        <h2>社区旧衣回收预约分拣<br/>与公益去向公示平台</h2>
        <p>居民 · 回收员 · 分拣中心 · 社区<br/>公益机构 · 积分财务 · 同一回收单协同</p>
        <ul>
          <li>预约上门 · 称重拍照 · 积分/捐赠双确认</li>
          <li>五分类复核 · 隐私衣物脱敏拒收</li>
          <li>批次签收 · 再生处理量全流程公示</li>
          <li>六类投诉在同一单中跨角色处理</li>
        </ul>
      </div>
      <div class="login-right">
        <h3>账号登录</h3>
        <el-form @submit.prevent="doLogin">
          <el-form-item>
            <el-input v-model="username" size="large" placeholder="用户名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="password" size="large" type="password" placeholder="密码"
                      :prefix-icon="Lock" show-password @keyup.enter="doLogin" />
          </el-form-item>
          <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="doLogin">
            登 录
          </el-button>
        </el-form>
        <el-divider>演示账号（密码均为 123456）</el-divider>
        <div class="quick">
          <el-button v-for="a in accounts" :key="a.u" size="small" round @click="fill(a)">
            {{ a.label }}
          </el-button>
        </div>
        <div class="to-public">
          <router-link to="/public">👀 不登录，先去看公益去向公示 →</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuth } from '../store'

const router = useRouter()
const auth = useAuth()
const username = ref('resident')
const password = ref('123456')
const loading = ref(false)

const accounts = [
  { u: 'resident', label: '居民·李晓梅' },
  { u: 'collector', label: '回收员·周强' },
  { u: 'sorter', label: '分拣中心·刘芳' },
  { u: 'community', label: '社区·孙丽' },
  { u: 'org', label: '公益机构·暖阳' },
  { u: 'finance', label: '积分财务·吴会计' },
  { u: 'admin', label: '平台管理员' }
]
function fill(a) { username.value = a.u; password.value = '123456' }

async function doLogin() {
  if (!username.value) return ElMessage.warning('请输入用户名')
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    ElMessage.success('登录成功')
    router.push('/app/dashboard')
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #dff3ea 0%, #f4f6f8 55%);
  padding: 24px;
}
.login-card {
  display: flex;
  width: 920px;
  max-width: 100%;
  background: #fff;
  border-radius: 18px;
  overflow: hidden;
  box-shadow: 0 18px 50px rgba(30, 80, 60, .15);
}
.login-left { padding: 42px 38px; flex: 1.1; }
.logo { font-size: 22px; font-weight: 800; margin-bottom: 26px; }
.login-left h2 { font-size: 23px; line-height: 1.5; margin: 0 0 14px; }
.login-left p { opacity: .92; line-height: 1.8; font-size: 13px; }
.login-left ul { padding-left: 18px; font-size: 13px; line-height: 2; opacity: .95; margin-top: 18px; }
.login-right { flex: 1; padding: 46px 42px; }
.login-right h3 { margin: 0 0 24px; }
.quick { display: flex; flex-wrap: wrap; gap: 8px; }
.to-public { text-align: center; margin-top: 20px; font-size: 13px; }
@media (max-width: 760px) {
  .login-left { display: none; }
}
</style>
