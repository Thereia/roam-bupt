import http from './request'
import { listPosts, getPostDetail, listComments, withMock } from '../mock'

// 默认走 mock；后端帖子接口就绪后把 VITE_USE_MOCK 设为 false
const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

// 评论接口属 P1，后端尚未提供；P0 只渲染空态，不发请求（否则 401 会把整页弹到登录）
export const COMMENTS_READY = false

export function getPosts(params = {}) {
  if (!USE_MOCK) return http.get('/posts', { params })
  return withMock(() => listPosts(params))
}

export function getPost(id) {
  if (!USE_MOCK) return http.get(`/posts/${id}`)
  return withMock(() => getPostDetail(id))
}

export function getComments(postId, params = {}) {
  if (!USE_MOCK) return http.get(`/posts/${postId}/comments`, { params })
  return withMock(() => listComments(postId, params))
}
