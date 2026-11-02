import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由表：
 *   /login      登录/注册（不需要 token）
 *   其余页面都需要登录（路由守卫检查 localStorage.token）
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/pages/LoginPage.vue') },
    { path: '/', name: 'home', component: () => import('@/pages/HomePage.vue'), meta: { requiresAuth: true } },
    { path: '/game/:id', name: 'gameDetail', component: () => import('@/pages/GameDetailPage.vue'), meta: { requiresAuth: true } },
    { path: '/recommend', name: 'recommend', component: () => import('@/pages/RecommendPage.vue'), meta: { requiresAuth: true } },
    { path: '/qa', name: 'qa', component: () => import('@/pages/QaPage.vue'), meta: { requiresAuth: true } },
    { path: '/profile', name: 'profile', component: () => import('@/pages/ProfilePage.vue'), meta: { requiresAuth: true } },
  ],
})

// 路由守卫：未登录访问受保护页面 → 跳登录页；已登录访问登录页 → 跳首页
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) return '/login'
  if (to.path === '/login' && token) return '/'
  return true
})

export default router
