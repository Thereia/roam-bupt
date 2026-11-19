import http from './request'
import { listPosts, getPostDetail, listComments, withMock } from '../mock'

// 默认走 mock；后端帖子接口就绪后把 VITE_USE_MOCK 设为 false
const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

// 评论接口已上线（P1）；置 false 可临时关掉评论请求
export const COMMENTS_READY = true

export function getPosts(params = {}) {
  if (!USE_MOCK) return http.get('/posts', { params })
  return withMock(() => listPosts(params))
}

export function createPost(payload) {
  return http.post('/posts', payload)
}

export function getPost(id) {
  if (!USE_MOCK) return http.get(`/posts/${id}`)
  return withMock(() => getPostDetail(id))
}

export function likePost(id) {
  if (!USE_MOCK) return http.post(`/posts/${id}/like`)
  return withMock(() => ({ active: true, count: 1 }))
}

export function unlikePost(id) {
  if (!USE_MOCK) return http.delete(`/posts/${id}/like`)
  return withMock(() => ({ active: false, count: 0 }))
}

export function collectPost(id) {
  if (!USE_MOCK) return http.post(`/posts/${id}/collect`)
  return withMock(() => ({ active: true, count: 1 }))
}

export function uncollectPost(id) {
  if (!USE_MOCK) return http.delete(`/posts/${id}/collect`)
  return withMock(() => ({ active: false, count: 0 }))
}

export function getComments(postId, params = {}) {
  if (!USE_MOCK) return http.get(`/posts/${postId}/comments`, { params })
  return withMock(() => listComments(postId, params))
}
