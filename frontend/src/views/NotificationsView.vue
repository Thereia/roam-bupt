<script setup>
import { onMounted, ref, watch } from 'vue'
import { getNotifications, markAllRead, markRead } from '../api/notify'
import { formatRelativeTime } from '../utils/time'
import { decreaseUnread, notifyStore, refreshUnread, setUnread } from '../store/notify'

const items = ref([])
const loading = ref(true)
const error = ref('')
const page = ref(1)
const pageSize = 10
const total = ref(0)

const totalPages = () => Math.max(1, Math.ceil(total.value / pageSize))

async function load(target = page.value) {
  loading.value = true
  error.value = ''
  try {
    const data = await getNotifications({ page: target, pageSize })
    items.value = data.list
    total.value = data.total
    page.value = target
    await refreshUnread()
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function readAll() {
  await markAllRead()
  items.value = items.value.map((item) => ({ ...item, read: true }))
  setUnread(0)
}

async function readOne(item) {
  if (item.read) return
  await markRead([item.id])
  item.read = true
  decreaseUnread()
}

function prev() {
  if (page.value > 1) load(page.value - 1)
}

function next() {
  if (page.value < totalPages()) load(page.value + 1)
}

onMounted(() => load(1))
watch(() => notifyStore.unread, () => {})
</script>

<template>
  <div class="page page-one">
    <div class="card">
      <div class="notify-head">
        <h1 class="notify-title">通知</h1>
        <span class="muted-text">{{ notifyStore.unread }} 条未读</span>
        <button v-if="notifyStore.unread" type="button" class="link-button" @click="readAll">全部已读</button>
      </div>

      <p v-if="error" class="form-error">{{ error }}</p>
      <p v-if="loading" class="muted-text">正在加载…</p>
      <p v-else-if="!items.length" class="muted-text">还没有通知。</p>

      <ul v-else class="notify-list">
        <li
          v-for="item in items"
          :key="item.id"
          class="notify-item"
          :class="{ unread: !item.read }"
          @click="readOne(item)"
        >
          <RouterLink :to="`/posts/${item.postId}`" class="notify-link">
            <span class="avatar small">{{ (item.actor?.nickname || '?').slice(0, 1) }}</span>
            <span class="notify-text">
              <b>{{ item.actor?.nickname || '某人' }}</b> {{ item.content }}
              <span v-if="item.postTitle" class="notify-post">《{{ item.postTitle }}》</span>
            </span>
            <span class="notify-time">{{ formatRelativeTime(item.createdTime) }}</span>
          </RouterLink>
        </li>
      </ul>

      <div v-if="total > pageSize" class="notify-pager">
        <button type="button" :disabled="page <= 1" @click="prev">上一页</button>
        <span class="muted-text">第 {{ page }} / {{ totalPages() }} 页 · 共 {{ total }} 条</span>
        <button type="button" :disabled="page >= totalPages()" @click="next">下一页</button>
      </div>
    </div>
  </div>
</template>
