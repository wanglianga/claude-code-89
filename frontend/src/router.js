import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/public' },
  { path: '/public', component: () => import('./views/PublicView.vue'), meta: { title: '公益去向公示' } },
  { path: '/login', component: () => import('./views/LoginView.vue'), meta: { title: '登录' } },
  {
    path: '/app',
    component: () => import('./views/Workspace.vue'),
    children: [
      { path: '', redirect: '/app/dashboard' },
      { path: 'dashboard', component: () => import('./views/DashboardView.vue'), meta: { title: '工作台' } },
      { path: 'orders', component: () => import('./views/OrdersView.vue'), meta: { title: '回收单' } },
      { path: 'complaints', component: () => import('./views/ComplaintsView.vue'), meta: { title: '投诉协同' } },
      { path: 'points', component: () => import('./views/PointsView.vue'), meta: { title: '环保积分' } },
      { path: 'batches', component: () => import('./views/BatchesView.vue'), meta: { title: '批次与去向' } },
      { path: 'partners', component: () => import('./views/PartnersView.vue'), meta: { title: '合作与帮扶' } },
      { path: 'aid', component: () => import('./views/AidView.vue'), meta: { title: '定向领取' } },
      { path: 'review', component: () => import('./views/ReviewView.vue'), meta: { title: '社区复盘' } }
    ]
  }
]

const router = createRouter({ history: createWebHashHistory(), routes })
export default router
