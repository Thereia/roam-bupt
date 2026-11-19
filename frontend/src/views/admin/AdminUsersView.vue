<script setup>
import { onMounted, ref } from 'vue'
import { changeUserStatus, getAdminUsers } from '../../api/admin'
import { userStore } from '../../store/user'

const keyword = ref('')
const users = ref([])
const total = ref(0)
const loading = ref(true)
const error = ref('')
const busy = ref(0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await getAdminUsers({ q: keyword.value.trim(), pageSize: 20 })
    users.value = page.list
    total.value = page.total
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function toggleBan(user) {
  if (user.id === userStore.user?.id) {
    error.value = '不能封禁自己'
    return
  }
  busy.value = user.id
  try {
    await changeUserStatus(user.id, user.status === 0 ? 1 : 0)
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
      <h1 class="section-title">用户管理</h1>
      <form class="admin-search" @submit.prevent="load">
        <input v-model="keyword" placeholder="搜用户名或昵称" />
        <button type="submit">搜索</button>
      </form>
      <span class="muted-text">共 {{ total }} 人</span>
    </div>

    <p v-if="loading" class="muted-text">正在加载…</p>
    <p v-else-if="error" class="muted-text">{{ error }}</p>
    <p v-else-if="!users.length" class="muted-text">没有匹配的用户。</p>

    <table v-else class="admin-table">
      <thead>
        <tr><th>用户</th><th>帖子</th><th>粉丝</th><th>关注</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="user in users" :key="user.id">
          <td>
            <RouterLink :to="`/users/${user.id}`" class="admin-link">{{ user.nickname }}</RouterLink>
            <span class="muted-text"> @{{ user.username }}</span>
          </td>
          <td>{{ user.postCount }}</td>
          <td>{{ user.followerCount }}</td>
          <td>{{ user.followingCount }}</td>
          <td class="admin-actions">
            <button type="button" :class="{ danger: user.status !== 0 }" :disabled="busy === user.id" @click="toggleBan(user)">
              {{ user.status === 0 ? '解封' : '封禁' }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
