import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/pages/AdminLoginPage.vue') },
    { path: '/', name: 'dashboard', component: () => import('@/pages/DashboardPage.vue'), meta: { requiresAuth: true } },
    { path: '/crawl', name: 'crawl', component: () => import('@/pages/CrawlPage.vue'), meta: { requiresAuth: true } },
    { path: '/cluster', name: 'cluster', component: () => import('@/pages/ClusterPage.vue'), meta: { requiresAuth: true } },
    { path: '/behavior', name: 'behavior', component: () => import('@/pages/BehaviorPage.vue'), meta: { requiresAuth: true } },
    { path: '/qa', name: 'qaStats', component: () => import('@/pages/QaStatsPage.vue'), meta: { requiresAuth: true } },
  ],
})

// 路由守卫：必须登录且身份是 ADMIN
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  let role = ''
  try { role = JSON.parse(localStorage.getItem('user') || '{}').role || '' } catch { role = '' }
  const isAdmin = !!token && role === 'ADMIN'
  if (to.meta.requiresAuth && !isAdmin) return '/login'
  if (to.path === '/login' && isAdmin) return '/'
  return true
})

export default router
