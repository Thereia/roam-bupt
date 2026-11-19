import http from './request'

export function uploadImage(file) {
  const form = new FormData()
  form.append('file', file)
  return http.post('/files/images', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
