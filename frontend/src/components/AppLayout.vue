<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { notifyStore, refreshUnread } from '../store/notify'
import { logout } from '../api/auth'
import { clearSession, isLoggedIn, userStore } from '../store/user'

const route = useRoute()
const router = useRouter()

const nickname = computed(() => userStore.user?.nickname || userStore.user?.username || '')
const initial = computed(() => nickname.value.slice(0, 1) || '游')
const activeSection = computed(() => {
  const path = route.path
  if (path === '/') return 'home'
  if (path === '/topics' || path.startsWith('/topics/')) return 'topics'
  if (path.startsWith('/nav')) return 'nav'
  if (path.startsWith('/search')) return 'search'
  if (path.startsWith('/publish')) return 'publish'
  return ''
})

async function handleLogout() {
  try {
    await logout()
  } catch {
    // 后端不可用时也允许本地退出，别把用户卡在登录态里
  }
  clearSession()
  router.push('/')
}

onMounted(refreshUnread)
watch(() => route.path, refreshUnread)
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <div class="site-header-inner">
      <RouterLink to="/" class="wordmark">roam<span class="accent">.bupt</span></RouterLink>
      <nav class="site-tabs">
        <RouterLink to="/" :class="{ on: activeSection === 'home' }">首页</RouterLink>
        <RouterLink to="/topics" :class="{ on: activeSection === 'topics' }">地点</RouterLink>
        <RouterLink to="/nav" :class="{ on: activeSection === 'nav' }">路线</RouterLink>
        <RouterLink to="/search" :class="{ on: activeSection === 'search' }">搜索</RouterLink>
        <RouterLink to="/publish" :class="{ on: activeSection === 'publish' }">发布</RouterLink>
        <RouterLink v-if="userStore.user?.role === 'ADMIN'" to="/admin">后台</RouterLink>
      </nav>
      <div class="topright">
        <template v-if="userStore.token">
          <RouterLink v-if="isLoggedIn()" to="/drafts" class="link-button">草稿</RouterLink>
          <RouterLink v-if="isLoggedIn()" to="/collections" class="link-button">收藏</RouterLink>
          <RouterLink to="/notifications" class="link-button">
            通知<span v-if="notifyStore.unread" class="nav-badge">{{ notifyStore.unread }}</span>
          </RouterLink>
          <span class="nickname">{{ nickname }}</span>
          <button type="button" class="link-button" @click="handleLogout">退出</button>
          <RouterLink :to="`/users/${userStore.user?.id ?? ''}`" class="avatar" :title="nickname">{{ initial }}</RouterLink>
        </template>
        <RouterLink v-else to="/login" class="link-button">登录</RouterLink>
      </div>
      </div>
    </header>
    <main class="shell">
      <slot />
    </main>
  </div>
</template>
