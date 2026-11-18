// mockup 里浏览数写成 1.2k，这里照同一口径格式化
export function formatCount(value) {
  const count = Number(value) || 0
  if (count < 1000) return String(count)
  return `${(count / 1000).toFixed(1).replace(/\.0$/, '')}k`
}
