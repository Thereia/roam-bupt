<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { search } from '../api/search'
import PostCard from '../components/PostCard.vue'

const route = useRoute()
const router = useRouter()

const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
const results = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const searched = ref(false)

async function submit() {
  const q = keyword.value.trim()
  if (!q) return
  loading.value = true
  error.value = ''
  searched.value = true
  try {
    router.replace({ path: '/search', query: { q } })
    const page = await search({ q, pageSize: 10 })
    results.value = page.list
    total.value = page.total
  } catch (err) {
    error.value = err.message || '搜索失败'
    results.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (keyword.value.trim()) submit()
})
</script>

<template>
  <div class="page page-one">
    <div class="card">
      <form class="search-form" @submit.prevent="submit">
        <input v-model="keyword" class="search-input" type="search" placeholder="搜帖子、地点、攻略" maxlength="50" />
        <button type="submit" class="search-button">搜索</button>
      </form>

      <p v-if="loading" class="muted-text">正在搜索…</p>
      <p v-else-if="error" class="muted-text">{{ error }}</p>
      <p v-else-if="searched && !results.length" class="muted-text">没有找到相关内容。</p>
      <p v-else-if="searched" class="muted-text">共 {{ total }} 条结果</p>

      <div v-if="results.length" class="search-results">
        <PostCard v-for="post in results" :key="post.id" :post="post" />
      </div>
    </div>
  </div>
</template>
