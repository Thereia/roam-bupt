<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { followUser, getUserPosts, getUserProfile, unfollowUser, updateMe } from '../api/user'
import { uploadImage } from '../api/file'
import { showNotice } from '../store/notice'
import PostCard from '../components/PostCard.vue'
import StateBlock from '../components/StateBlock.vue'
import { isLoggedIn, updateStoredUser, userStore } from '../store/user'

const route = useRoute()
const router = useRouter()

const profile = ref(null)
const posts = ref([])
const loading = ref(true)
const error = ref('')
const following = ref(false)
const busy = ref(false)

const isSelf = computed(() => userStore.user && profile.value && userStore.user.id === profile.value.id)

const draftCount = ref(0)
const editing = ref(false)
const saving = ref(false)
const uploading = ref(false)
const form = ref({ nickname: '', bio: '', avatar: '' })

function startEdit() {
  form.value = {
    nickname: profile.value.nickname || '',
    bio: profile.value.bio || '',
    avatar: profile.value.avatar || '',
  }
  editing.value = true
}

async function onAvatarChange(event) {
  const file = event.target.files && event.target.files[0]
  if (!file) return
  uploading.value = true
  try {
    const result = await uploadImage(file)
    form.value.avatar = result.url
  } catch (err) {
    showNotice(err.message || '上传失败', 'error')
  } finally {
    uploading.value = false
  }
}

async function saveProfile() {
  if (saving.value) return
  saving.value = true
  try {
    const updated = await updateMe({
      nickname: form.value.nickname,
      bio: form.value.bio,
      avatar: form.value.avatar || null,
    })
    profile.value = { ...profile.value, nickname: updated.nickname, bio: updated.bio, avatar: updated.avatar }
    updateStoredUser({ nickname: updated.nickname, avatar: updated.avatar, bio: updated.bio })
    editing.value = false
    showNotice('资料已更新')
  } catch (err) {
    showNotice(err.message || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const id = route.params.id
    const [user, page] = await Promise.all([getUserProfile(id), getUserPosts(id, { pageSize: 10 })])
    profile.value = user
    following.value = user.isFollowed === true
    // 主页只展示已发布内容，草稿与待审不混进来
    posts.value = page.list.filter((post) => post.status === 'ONLINE')
    draftCount.value = page.list.filter((post) => post.status === 'DRAFT').length
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
          <span class="avatar big">
            <img v-if="profile.avatar" :src="profile.avatar" :alt="profile.nickname" class="avatar-img" />
            <template v-else>{{ (profile.nickname || '?').slice(0, 1) }}</template>
          </span>
          <div class="profile-meta">
            <h1 class="profile-name">{{ profile.nickname }}</h1>
            <p class="muted-text">@{{ profile.username }}<span v-if="profile.bio"> · {{ profile.bio }}</span></p>
            <div class="profile-counts">
              <span><b>{{ profile.postCount }}</b> 帖子</span>
              <RouterLink :to="`/users/${profile.id}/followers`"><b>{{ profile.followerCount }}</b> 粉丝</RouterLink>
              <RouterLink :to="`/users/${profile.id}/following`"><b>{{ profile.followingCount }}</b> 关注</RouterLink>
            </div>
          </div>
          <button v-if="isSelf" type="button" class="btn" @click="startEdit">编辑资料</button>
          <button
            v-else
            type="button"
            class="follow-button"
            :class="{ on: following }"
            :disabled="busy"
            @click="toggleFollow"
          >
            {{ following ? '已关注' : '关注' }}
          </button>
        </div>

        <div v-if="editing" class="card profile-edit">
          <h2 class="section-title">编辑资料</h2>
          <label>昵称</label>
          <input v-model="form.nickname" maxlength="32" />
          <label>简介</label>
          <textarea v-model="form.bio" maxlength="120" rows="2" placeholder="一句话介绍自己"></textarea>
          <label>头像</label>
          <div class="avatar-row">
            <span class="avatar big">
              <img v-if="form.avatar" :src="form.avatar" alt="头像预览" class="avatar-img" />
              <template v-else>{{ (form.nickname || '?').slice(0, 1) }}</template>
            </span>
            <input type="file" accept="image/png,image/jpeg,image/webp" :disabled="uploading" @change="onAvatarChange" />
            <span v-if="uploading" class="muted-text">上传中…</span>
          </div>
          <div class="edit-actions">
            <button type="button" class="btn primary" :disabled="saving" @click="saveProfile">
              {{ saving ? '保存中…' : '保存' }}
            </button>
            <button type="button" class="btn" @click="editing = false">取消</button>
          </div>
        </div>

        <div class="card">
          <h2 class="section-title">帖子</h2>
          <p v-if="isSelf && draftCount" class="muted-text">
            还有 {{ draftCount }} 条草稿在<RouterLink to="/drafts" class="link-button">草稿箱</RouterLink>。
          </p>
          <p v-if="!posts.length" class="muted-text">还没有帖子。</p>
          <PostCard v-for="post in posts" :key="post.id" :post="post" />
        </div>
      </template>
    </main>
  </div>
</template>
