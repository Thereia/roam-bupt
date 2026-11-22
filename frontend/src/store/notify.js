import { reactive } from 'vue'
import { getUnreadCount } from '../api/notify'
import { isLoggedIn } from './user'

/** 未读数共享给顶栏与通知页，避免两处各查一次、各改一次。 */
export const notifyStore = reactive({ unread: 0 })

export async function refreshUnread() {
  if (!isLoggedIn()) {
    notifyStore.unread = 0
    return
  }
  try {
    const data = await getUnreadCount()
    notifyStore.unread = data.count || 0
  } catch {
    // 未登录或接口不可用时保持原值，不打断页面
  }
}

export function setUnread(count) {
  notifyStore.unread = Math.max(0, count)
}

export function decreaseUnread(step = 1) {
  notifyStore.unread = Math.max(0, notifyStore.unread - step)
}
