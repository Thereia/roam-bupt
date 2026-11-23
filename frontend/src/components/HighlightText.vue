<script setup>
import { computed } from 'vue'

const props = defineProps({
  text: { type: String, default: '' },
  keyword: { type: String, default: '' },
})

// 按字面切分关键词，命中段包 <mark class="hl">
const parts = computed(() => {
  const raw = String(props.text || '')
  const keyword = String(props.keyword || '').trim()
  if (!keyword) return [{ text: raw, hit: false }]
  const lower = keyword.toLowerCase()
  const result = []
  let rest = raw
  while (rest) {
    const index = rest.toLowerCase().indexOf(lower)
    if (index < 0) {
      result.push({ text: rest, hit: false })
      break
    }
    if (index > 0) result.push({ text: rest.slice(0, index), hit: false })
    result.push({ text: rest.slice(index, index + keyword.length), hit: true })
    rest = rest.slice(index + keyword.length)
  }
  return result.length ? result : [{ text: raw, hit: false }]
})
</script>

<template>
  <template v-for="(part, index) in parts" :key="index">
    <mark v-if="part.hit" class="hl">{{ part.text }}</mark>
    <template v-else>{{ part.text }}</template>
  </template>
</template>
