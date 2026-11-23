import axios from 'axios'
import router from '../router'
import { TOKEN_KEY, clearSession } from '../store/user'
import { notice, showNotice } from '../store/notice'

export { notice, showNotice }

export class ApiError extends Error {
  constructor(code, message, httpStatus = 0) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
  }
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 登录/注册失败也走 401/429，交给表单就地显示，不弹全局提示、不清已有登录态
function isAuthAttempt(url = '') {
  return /\/auth\/(login|register)$/.test(url)
}

function authMessage(status, serverMessage) {
  if (serverMessage) return serverMessage
  if (status === 401) return '用户名或密码错误'
  if (status === 403) return '没有权限'
  if (status === 429) return '操作过于频繁，请稍后再试'
  if (status === 503) return '服务暂时不可用，请稍后重试'
  if (status) return `请求失败（${status}）`
  return '无法连接服务器，请检查后端是否已启动'
}

function redirectToLogin() {
  if (router.currentRoute.value.path === '/login') return
  router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
}

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (!body || typeof body.code !== 'number') {
      throw new ApiError(-1, '返回格式不正确', response.status)
    }
    if (body.code === 0) return body.data
    throw new ApiError(body.code, body.message || '请求失败', response.status)
  },
  (error) => {
    if (error instanceof ApiError) return Promise.reject(error)

    const status = error.response?.status || 0
    const serverMessage = error.response?.data?.message
    // 表单类页面自己就地显示错误，传 silent 避免同一错误弹两次
    const silent = error.config?.silent === true

    if (isAuthAttempt(error.config?.url)) {
      return Promise.reject(new ApiError(status || -1, authMessage(status, serverMessage), status))
    }

    if (status === 401) {
      clearSession()
      if (!silent) showNotice('登录已过期，请重新登录', 'error')
      redirectToLogin()
      return Promise.reject(new ApiError(401, '未登录或登录已过期', 401))
    }

    if (status === 403) {
      if (!silent) showNotice('没有权限', 'error')
      return Promise.reject(new ApiError(403, '没有权限', 403))
    }

    if (status === 429) {
      const tooFast = serverMessage || '操作太频繁，每分钟 5 次，请稍后再试'
      if (!silent) showNotice(tooFast, 'error')
      return Promise.reject(new ApiError(429, tooFast, 429))
    }

    if (status === 503) {
      // 认证依赖故障不是登录态失效，不能清 token
      if (!silent) showNotice('服务暂时不可用，请稍后重试', 'error')
      return Promise.reject(new ApiError(503, '服务暂时不可用，请稍后重试', 503))
    }

    const message = authMessage(status, serverMessage)
    if (!silent) showNotice(message, 'error')
    return Promise.reject(new ApiError(status || -1, message, status))
  },
)

export default http
