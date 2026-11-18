import http from './request'

export function login(payload) {
  return http.post('/auth/login', payload)
}

export function register(payload) {
  return http.post('/auth/register', payload)
}

export function logout() {
  return http.post('/auth/logout')
}
