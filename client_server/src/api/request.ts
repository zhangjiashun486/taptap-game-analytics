import axios from 'axios'

/**
 * 统一请求实例（项目规则要求：所有页面必须用它，禁止页面里直接 import axios）。
 * 职责：
 *   1) 统一 baseURL（来自 .env 的 VITE_API_BASE_URL）
 *   2) 自动携带 token（localStorage.token → Authorization: Bearer xxx）
 *   3) 自动解包后端返回的 { code, message, data }，页面直接拿 data
 *   4) 401 时清除 token 并跳转登录页
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
})

// 请求拦截器：每个请求自动带上 token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一解包 + 错误处理
request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code === 200) {
      return body.data          // 页面直接拿 data
    }
    const message = body?.message || '请求失败'
    if (body?.code === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (location.pathname !== '/login') location.href = '/login'
    }
    if (body?.code === 403) {
      alert('需要管理员权限')
    }
    return Promise.reject(new Error(message))
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (location.pathname !== '/login') location.href = '/login'
    }
    const message = error.response?.data?.message || error.message || '网络错误'
    return Promise.reject(new Error(message))
  },
)

export default request
