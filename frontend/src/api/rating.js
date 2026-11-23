import http from './request'

export function getRatings(topicId) {
  return http.get(`/topics/${topicId}/ratings`)
}

export function rateTopic(topicId, score) {
  return http.put(`/topics/${topicId}/ratings`, { score })
}

export function removeRating(topicId) {
  return http.delete(`/topics/${topicId}/ratings`)
}
