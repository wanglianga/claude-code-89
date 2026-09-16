import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({ baseURL: '/' })

api.interceptors.request.use(cfg => {
  const token = localStorage.getItem('token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

api.interceptors.response.use(
  res => res.data,
  err => {
    if (err.response?.status === 401 && !err.config.url.includes('/auth/login')) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (location.hash !== '#/login' && !location.hash.startsWith('#/public')) {
        location.hash = '#/login'
      }
    }
    const msg = err.response?.data?.message || err.message || '请求失败'
    if (!err.config?.silent) ElMessage.error(typeof msg === 'string' ? msg : '请求失败')
    return Promise.reject(err)
  }
)

export default api
