import http from './request'

export function getUserProfile(id) {
  return http.get(`/users/${id}`)
}

export function getUserPosts(id, params = {}) {
  return http.get(`/users/${id}/posts`, { params })
}

export function followUser(id) {
  return http.post(`/users/${id}/follow`)
}

export function unfollowUser(id) {
  return http.delete(`/users/${id}/follow`)
}

export function updateMe(payload) {
  return http.put('/users/me', payload, { silent: true })
}
