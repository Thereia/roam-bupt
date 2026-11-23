<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deletePost, getUserDrafts, publishDraft } from '../api/post'
import { showNotice } from '../store/notice'
import { userStore } from '../store/user'

const router = useRouter()
const drafts = ref([])
const loading = ref(true)
const error = ref('')
const busy = ref(0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await getUserDrafts(userStore.user.id)
    drafts.value = page.list
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function publish(draft) {
  busy.value = draft.id
  try {
    await publishDraft(draft.id)
    showNotice('已发布')
    router.push(`/posts/${draft.id}`)
  } catch (err) {
    showNotice(err.message || '发布失败', 'error')
  } finally {
    busy.value = 0
  }
}

async function remove(draft) {
  if (!window.confirm('草稿删除后不可恢复，确定删除？')) return
  busy.value = draft.id
  try {
    await deletePost(draft.id)
    drafts.value = drafts.value.filter((item) => item.id !== draft.id)
  } catch (err) {
    showNotice(err.message || '删除失败', 'error')
  } finally {
    busy.value = 0
  }
}

onMounted(load)
</script>

<template>
  <div class="page page-one">
    <div class="card">
      <div class="notify-head">
        <h1 class="section-title">草稿箱</h1>
        <RouterLink to="/publish" class="link-button">写新帖</RouterLink>
      </div>

      <p v-if="loading" class="muted-text">正在加载…</p>
      <p v-else-if="error" class="form-error">{{ error }}</p>
      <p v-else-if="!drafts.length" class="muted-text">没有草稿。写帖时点「存草稿」就会出现在这里。</p>

      <div v-for="draft in drafts" v-else :key="draft.id" class="draft-item">
        <div class="draft-main">
          <RouterLink :to="`/posts/${draft.id}`" class="draft-title">{{ draft.title }}</RouterLink>
          <span class="muted-text">{{ draft.excerpt }}</span>
        </div>
        <div class="draft-actions">
          <RouterLink :to="{ path: '/publish', query: { edit: draft.id } }" class="link-button">继续编辑</RouterLink>
          <button type="button" class="link-button" :disabled="busy === draft.id" @click="publish(draft)">发布</button>
          <button type="button" class="link-button" :disabled="busy === draft.id" @click="remove(draft)">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>
