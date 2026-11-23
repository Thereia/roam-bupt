import { reactive } from 'vue'

/** 全局轻提示；页面内错误自己就地显示，不要重复弹。 */
export const notice = reactive({ text: '', kind: 'info' })
let timer = 0

export function showNotice(text, kind = 'info') {
  notice.text = text
  notice.kind = kind
  window.clearTimeout(timer)
  timer = window.setTimeout(() => {
    notice.text = ''
  }, 4000)
}
