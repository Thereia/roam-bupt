import http from './request'
import { listTopics, getTopicDetail, listTopicPosts, withMock } from '../mock'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

export function getTopics(params = {}) {
  if (!USE_MOCK) return http.get('/topics', { params })
  return withMock(() => listTopics(params))
}

export function getTopic(id) {
  if (!USE_MOCK) return http.get(`/topics/${id}`)
  return withMock(() => getTopicDetail(id))
}

export function getTopicPosts(id, params = {}) {
  if (!USE_MOCK) return http.get(`/topics/${id}/posts`, { params })
  return withMock(() => listTopicPosts(id, params))
}
