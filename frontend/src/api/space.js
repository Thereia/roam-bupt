import http from './request'

export function getMap() {
  return http.get('/nav/map')
}

export function planRoutes(stops, mode = 'MIXED') {
  return http.post('/nav/routes', { stops: stops.map((topicId) => ({ topicId })), mode })
}

export function getPostRoute(id, mode = 'MIXED') {
  return http.get(`/posts/${id}/route`, { params: { mode } })
}
