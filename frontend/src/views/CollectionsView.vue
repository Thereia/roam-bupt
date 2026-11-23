<script setup>
import { onMounted, ref } from 'vue'
import { getMyCollections, uncollectPost } from '../api/post'
import PostCard from '../components/PostCard.vue'

const posts = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(true)
const error = ref('')

async function load(target = page.value) {
  loading.value = true
  error.value = ''
  try {
    const data = await getMyCollections({ page: target, pageSize })
    posts.value = data.list
    total.value = data.total
    page.value = target
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function prev() {
  if (page.value > 1) load(page.value - 1)
}

function next() {
  if (page.value * pageSize < total.value) load(page.value + 1)
}

async function remove(post) {
  try {
    await uncollectPost(post.id)
    posts.value = posts.value.filter((item) => item.id !== post.id)
    total.value = Math.max(0, total.value - 1)
  } catch (err) {
    error.value = err.message || '取消收藏失败'
  }
}

onMounted(() => load(1))
</script>

<template>
  <div class="page page-one">
    <div class="card">
      <div class="notify-head">
        <h1 class="section-title">我的收藏</h1>
        <span class="muted-text">共 {{ total }} 条</span>
      </div>

      <p v-if="loading" class="muted-text">正在加载…</p>
      <p v-else-if="error" class="form-error">{{ error }}</p>
      <p v-else-if="!posts.length" class="muted-text">还没有收藏的帖子，点帖子里的「收藏」就会出现在这里。</p>

      <div v-else>
        <div v-for="post in posts" :key="post.id" class="collected-item">
          <PostCard :post="post" variant="home" />
          <button type="button" class="link-button" @click="remove(post)">取消收藏</button>
        </div>
      </div>

      <div v-if="total > pageSize" class="notify-pager">
        <button type="button" :disabled="page <= 1" @click="prev">上一页</button>
        <span class="muted-text">第 {{ page }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button type="button" :disabled="page * pageSize >= total" @click="next">下一页</button>
      </div>
    </div>
  </div>
</template>
