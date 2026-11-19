import http from './request'

export function getNotifications(params = {}) {
  return http.get('/notifications', { params })
}

export function getUnreadCount() {
  return http.get('/notifications/unread-count')
}

export function markRead(ids) {
  return http.post('/notifications/read', { ids })
}

export function markAllRead() {
  return http.put('/notifications/read-all')
}
