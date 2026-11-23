<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  images: {
    type: Array,
    required: true,
  },
  index: {
    type: Number,
    default: 0,
  },
})

const emit = defineEmits(['close'])

const current = ref(0)
let previousBodyOverflow = ''

const total = computed(() => props.images.length)
const multi = computed(() => total.value > 1)
const currentUrl = computed(() => props.images[current.value] || '')
const countLabel = computed(() => `${current.value + 1}/${total.value}`)

function normalize(value) {
  if (!total.value) return 0
  const number = Number.isInteger(value) ? value : 0
  return Math.min(Math.max(number, 0), total.value - 1)
}

function close() {
  emit('close')
}

function prev() {
  if (!multi.value) return
  current.value = (current.value - 1 + total.value) % total.value
}

function next() {
  if (!multi.value) return
  current.value = (current.value + 1) % total.value
}

function onKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }
  if (!multi.value) return
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    prev()
  } else if (event.key === 'ArrowRight') {
    event.preventDefault()
    next()
  }
}

watch(() => props.index, (value) => {
  current.value = normalize(value)
}, { immediate: true })

watch(total, () => {
  current.value = normalize(current.value)
})

onMounted(() => {
  // 灯箱打开期间锁住页面滚动，关闭时还原原来的值
  previousBodyOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.body.style.overflow = previousBodyOverflow
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div class="lightbox" role="dialog" aria-modal="true" aria-label="查看大图" @click.self="close">
      <img class="lightbox-img" :src="currentUrl" :alt="`第 ${current + 1} 张图`" />
      <button type="button" class="lightbox-close" aria-label="关闭" @click="close">×</button>
      <template v-if="multi">
        <button type="button" class="lightbox-nav lightbox-prev" aria-label="上一张" @click="prev">‹</button>
        <button type="button" class="lightbox-nav lightbox-next" aria-label="下一张" @click="next">›</button>
        <span class="lightbox-count">{{ countLabel }}</span>
      </template>
    </div>
  </Teleport>
</template>
