import axios from 'axios'

/**
 * 管理端统一请求实例：自动带 token、解包 data、401/403 跳登录。
 * 页面里不允许直接使用 axios（项目规则）。
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code === 200) return body.data
    if (body?.code === 401 || body?.code === 403) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (location.pathname !== '/login') location.href = '/login'
    }
    return Promise.reject(new Error(body?.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    if (status === 401 || status === 403) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (location.pathname !== '/login') location.href = '/login'
    }
    return Promise.reject(new Error(error.response?.data?.message || error.message || '网络错误'))
  },
)

export default request
