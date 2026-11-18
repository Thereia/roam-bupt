const pad = (value) => String(value).padStart(2, '0')

// 后端时间是 yyyy-MM-dd HH:mm:ss，Safari 不接受带空格的解析结果，先换成 T
function parseTime(value) {
  const date = new Date(String(value || '').replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? null : date
}

function startOfDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
}

// 按自然日算，昨天 21:40 不能显示成「20 小时前」
function daysBetween(date) {
  return Math.round((startOfDay(new Date()) - startOfDay(date)) / (24 * 60 * 60 * 1000))
}

function clock(date) {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function formatRelativeTime(value) {
  const date = parseTime(value)
  if (!date) return String(value || '')

  const diff = Date.now() - date.getTime()
  const day = daysBetween(date)

  if (day <= 0) {
    if (diff < 60 * 1000) return '刚刚'
    if (diff < 60 * 60 * 1000) return `${Math.floor(diff / (60 * 1000))} 分钟前`
    return `${Math.floor(diff / (60 * 60 * 1000))} 小时前`
  }
  if (day === 1) return '昨天'
  if (day < 7) return `${day} 天前`
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function formatDayTime(value) {
  const date = parseTime(value)
  if (!date) return String(value || '')

  const day = daysBetween(date)
  if (day === 0) return `今天 ${clock(date)}`
  if (day === 1) return `昨天 ${clock(date)}`
  if (day < 7) return `${day} 天前 ${clock(date)}`
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${clock(date)}`
}
