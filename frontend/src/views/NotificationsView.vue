<script setup>
import { onMounted, ref } from 'vue'
import { getNotifications, markAllRead, markRead, getUnreadCount } from '../api/notify'
import { formatRelativeTime } from '../utils/time'

const items = ref([])
const loading = ref(true)
const error = ref('')
const unread = ref(0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [page, count] = await Promise.all([getNotifications({ pageSize: 20 }), getUnreadCount()])
    items.value = page.list
    unread.value = count.count
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function readAll() {
  await markAllRead()
  items.value = items.value.map((item) => ({ ...item, read: true }))
  unread.value = 0
}

async function readOne(item) {
  if (item.read) return
  await markRead([item.id])
  item.read = true
  unread.value = Math.max(0, unread.value - 1)
}

onMounted(load)
</script>

<template>
  <div class="page page-one">
    <div class="card">
      <div class="notify-head">
        <h1 class="notify-title">通知</h1>
        <span class="muted-text">{{ unread }} 条未读</span>
        <button v-if="unread" type="button" class="link-button" @click="readAll">全部已读</button>
      </div>

      <p v-if="loading" class="muted-text">正在加载…</p>
      <p v-else-if="error" class="muted-text">{{ error }}</p>
      <p v-else-if="!items.length" class="muted-text">还没有通知。</p>

      <ul v-else class="notify-list">
        <li v-for="item in items" :key="item.id" class="notify-item" :class="{ unread: !item.read }" @click="readOne(item)">
          <RouterLink :to="`/posts/${item.postId}`" class="notify-link">
            <span class="avatar small">{{ (item.actor?.nickname || '?').slice(0, 1) }}</span>
            <span class="notify-text">
              <b>{{ item.actor?.nickname || '某人' }}</b> {{ item.content }}
            </span>
            <span class="notify-time">{{ formatRelativeTime(item.createdTime) }}</span>
          </RouterLink>
        </li>
      </ul>
    </div>
  </div>
</template>
