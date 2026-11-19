<script setup>
import { onMounted, ref } from 'vue'
import { changePostStatus, getAdminPosts } from '../../api/admin'
import { formatRelativeTime } from '../../utils/time'

const tabs = [
  { key: 'PENDING', label: '待审核' },
  { key: 'ONLINE', label: '已发布' },
  { key: 'OFFLINE', label: '已下线' },
]
const status = ref('PENDING')
const posts = ref([])
const total = ref(0)
const loading = ref(true)
const error = ref('')
const busy = ref(0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await getAdminPosts({ status: status.value, pageSize: 20 })
    posts.value = page.list
    total.value = page.total
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function switchTab(key) {
  if (status.value === key) return
  status.value = key
  load()
}

async function setStatus(post, target) {
  busy.value = post.id
  try {
    await changePostStatus(post.id, target)
    await load()
  } catch (err) {
    error.value = err.message || '操作失败'
  } finally {
    busy.value = 0
  }
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="admin-head">
      <h1 class="section-title">内容审核</h1>
      <div class="admin-tabs">
        <button v-for="tab in tabs" :key="tab.key" type="button" :class="{ on: status === tab.key }" @click="switchTab(tab.key)">
          {{ tab.label }}
        </button>
      </div>
      <span class="muted-text">共 {{ total }} 条</span>
    </div>

    <p v-if="loading" class="muted-text">正在加载…</p>
    <p v-else-if="error" class="muted-text">{{ error }}</p>
    <p v-else-if="!posts.length" class="muted-text">这里没有内容。</p>

    <table v-else class="admin-table">
      <thead>
        <tr><th>标题</th><th>作者</th><th>挂载主题</th><th>时间</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="post in posts" :key="post.id">
          <td>
            <RouterLink :to="`/posts/${post.id}`" class="admin-link">{{ post.title }}</RouterLink>
          </td>
          <td>{{ post.author?.nickname || '—' }}</td>
          <td>{{ post.topics.map((topic) => topic.name).join(' · ') || '—' }}</td>
          <td class="admin-time">{{ formatRelativeTime(post.createdTime) }}</td>
          <td class="admin-actions">
            <button v-if="post.status === 'PENDING'" type="button" :disabled="busy === post.id" @click="setStatus(post, 'ONLINE')">通过</button>
            <button v-if="post.status === 'PENDING'" type="button" class="danger" :disabled="busy === post.id" @click="setStatus(post, 'DELETED')">驳回</button>
            <button v-if="post.status === 'ONLINE'" type="button" :disabled="busy === post.id" @click="setStatus(post, 'OFFLINE')">下线</button>
            <button v-if="post.status === 'OFFLINE'" type="button" :disabled="busy === post.id" @click="setStatus(post, 'ONLINE')">重新上线</button>
            <button v-if="post.status !== 'DELETED'" type="button" class="danger" :disabled="busy === post.id" @click="setStatus(post, 'DELETED')">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
