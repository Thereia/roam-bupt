import http from './request'

export function search(params = {}) {
  return http.get('/search', { params })
}
