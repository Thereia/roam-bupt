import http from './request'
import { listTopics, getTopicDetail, listTopicPosts, withMock } from '../mock'

// 默认走真实接口；只有显式 VITE_USE_MOCK=true 才用 mock
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

export function getTopics(params = {}) {
  if (!USE_MOCK) return http.get('/topics', { params })
  return withMock(() => listTopics(params))
}

export function getTopicSuggest(params = {}) {
  return http.get('/topics/suggest', { params })
}

export function getTopicStats() {
  if (!USE_MOCK) return http.get('/topics/stats')
  return withMock(() => [])
}

export function getPostStats() {
  if (!USE_MOCK) return http.get('/topics/stats/posts')
  return withMock(() => ({ total: 0, categories: [] }))
}

export function getTopic(id) {
  if (!USE_MOCK) return http.get(`/topics/${id}`)
  return withMock(() => getTopicDetail(id))
}

export function getTopicPosts(id, params = {}) {
  if (!USE_MOCK) return http.get(`/topics/${id}/posts`, { params })
  return withMock(() => listTopicPosts(id, params))
}
