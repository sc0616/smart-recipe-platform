import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = 'Bearer ' + token
  }
  return config
})

request.interceptors.response.use(
  res => {
    if (res.data.code === '200') {
      return res.data
    }
    if (res.data.code === '401') {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.clear()
      router.push('/login')
      return Promise.reject(res.data)
    }
    ElMessage.error(res.data.msg || '请求失败')
    return Promise.reject(res.data)
  },
  err => {
    ElMessage.error(err.message || '网络错误')
    return Promise.reject(err)
  }
)

export default request