import http from './request'
import { listPosts, getPostDetail, listComments, withMock } from '../mock'

// 默认走真实接口；只有显式 VITE_USE_MOCK=true 才用 mock
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

// 评论接口已上线（P1）；置 false 可临时关掉评论请求
export const COMMENTS_READY = true

export function getPosts(params = {}) {
  if (!USE_MOCK) return http.get('/posts', { params })
  return withMock(() => listPosts(params))
}

export function createPost(payload, config = {}) {
  return http.post('/posts', payload, config)
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

export function createComment(postId, content) {
  return http.post(`/posts/${postId}/comments`, { content })
}

export function getMyCollections(params = {}) {
  return http.get('/me/collections', { params })
}

export function getComments(postId, params = {}) {
  if (!USE_MOCK) return http.get(`/posts/${postId}/comments`, { params })
  return withMock(() => listComments(postId, params))
}

export function updatePost(id, payload, config = {}) {
  return http.put(`/posts/${id}`, payload, config)
}

export function deletePost(id) {
  return http.delete(`/posts/${id}`)
}

export function deleteComment(commentId) {
  return http.delete(`/comments/${commentId}`)
}

export function publishDraft(id) {
  return http.put(`/posts/${id}/publish`, null, { silent: true })
}

export async function getUserDrafts(userId) {
  const data = await http.get(`/users/${userId}/posts`, { params: { pageSize: 50 } })
  return { ...data, list: (data.list || []).filter((post) => post.status === 'DRAFT') }
}
