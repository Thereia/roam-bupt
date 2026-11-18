const pad = (value) => String(value).padStart(2, '0')

// 后端时间是 yyyy-MM-dd HH:mm:ss，Safari 不接受带空格的解析结果，先换成 T
export function formatRelativeTime(value) {
  const date = new Date(String(value || '').replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return String(value || '')

  const diff = Date.now() - date.getTime()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`

  const days = Math.floor(diff / day)
  if (days === 1) return '昨天'
  if (days < 7) return `${days} 天前`
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
