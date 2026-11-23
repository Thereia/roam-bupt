import http from './request'

export function getRatings(topicId, params = {}) {
  return http.get(`/topics/${topicId}/ratings`, { params })
}

export function submitRating(topicId, { score, review = '', images = [] }) {
  return http.put(`/topics/${topicId}/ratings`, { score, review, images })
}

export function removeRating(topicId) {
  return http.delete(`/topics/${topicId}/ratings`)
}
