import { reactive } from 'vue'

export const TOKEN_KEY = 'roam_token'
export const USER_KEY = 'roam_user'

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    // 存储被外部改坏时按游客处理，不让首屏挂掉
    return null
  }
}

export const userStore = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: readStoredUser(),
})

export function setSession(token, user) {
  userStore.token = token || ''
  userStore.user = user || null
  localStorage.setItem(TOKEN_KEY, userStore.token)
  localStorage.setItem(USER_KEY, JSON.stringify(userStore.user))
}

export function clearSession() {
  userStore.token = ''
  userStore.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function isLoggedIn() {
  return Boolean(userStore.token)
}

/** 资料修改后同步当前登录用户，避免刷新前后不一致。 */
export function updateStoredUser(patch) {
  if (!userStore.user) return
  userStore.user = { ...userStore.user, ...patch }
  localStorage.setItem(USER_KEY, JSON.stringify(userStore.user))
}
