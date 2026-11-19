<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { followUser, getUserPosts, getUserProfile, unfollowUser } from '../api/user'
import PostCard from '../components/PostCard.vue'
import StateBlock from '../components/StateBlock.vue'
import { isLoggedIn, userStore } from '../store/user'

const route = useRoute()
const router = useRouter()

const profile = ref(null)
const posts = ref([])
const loading = ref(true)
const error = ref('')
const following = ref(false)
const busy = ref(false)

const isSelf = computed(() => userStore.user && profile.value && userStore.user.id === profile.value.id)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const id = route.params.id
    const [user, page] = await Promise.all([getUserProfile(id), getUserPosts(id, { pageSize: 10 })])
    profile.value = user
    following.value = user.isFollowed === true
    posts.value = page.list
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function toggleFollow() {
  if (!isLoggedIn()) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (busy.value || !profile.value) return
  busy.value = true
  try {
    const result = following.value
      ? await unfollowUser(profile.value.id)
      : await followUser(profile.value.id)
    following.value = result.followed
    profile.value = { ...profile.value, followerCount: result.followerCount }
  } catch (err) {
    error.value = err.message || '操作失败'
  } finally {
    busy.value = false
  }
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<template>
  <div class="page page-two">
    <main>
      <div v-if="loading" class="card">
        <p class="muted-text">正在加载…</p>
      </div>
      <div v-else-if="error" class="card">
        <p class="muted-text">{{ error }}</p>
      </div>

      <template v-else>
        <div class="card profile-head">
          <span class="avatar big">{{ (profile.nickname || '?').slice(0, 1) }}</span>
          <div class="profile-meta">
            <h1 class="profile-name">{{ profile.nickname }}</h1>
            <p class="muted-text">@{{ profile.username }}<span v-if="profile.bio"> · {{ profile.bio }}</span></p>
            <div class="profile-counts">
              <span><b>{{ profile.postCount }}</b> 帖子</span>
              <span><b>{{ profile.followerCount }}</b> 粉丝</span>
              <span><b>{{ profile.followingCount }}</b> 关注</span>
            </div>
          </div>
          <button
            v-if="!isSelf"
            type="button"
            class="follow-button"
            :class="{ on: following }"
            :disabled="busy"
            @click="toggleFollow"
          >
            {{ following ? '已关注' : '关注' }}
          </button>
        </div>

        <div class="card">
          <h2 class="section-title">帖子</h2>
          <p v-if="!posts.length" class="muted-text">还没有帖子。</p>
          <PostCard v-for="post in posts" :key="post.id" :post="post" />
        </div>
      </template>
    </main>
  </div>
</template>
