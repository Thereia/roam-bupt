import http from './request'

export function getAdminPosts(params = {}) {
  return http.get('/admin/posts', { params })
}

export function changePostStatus(id, status) {
  return http.put(`/admin/posts/${id}/status`, { status })
}

export function getAdminUsers(params = {}) {
  return http.get('/admin/users', { params })
}

export function changeUserStatus(id, status) {
  return http.put(`/admin/users/${id}/status`, { status })
}

export function getSensitiveWords(params = {}) {
  return http.get('/admin/sensitive-words', { params })
}

export function addSensitiveWord(word) {
  return http.post('/admin/sensitive-words', { word })
}

export function removeSensitiveWord(id) {
  return http.delete(`/admin/sensitive-words/${id}`)
}
