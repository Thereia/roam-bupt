<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { search } from '../api/search'
import PostCard from '../components/PostCard.vue'
import { getTopics } from '../api/topic'

const route = useRoute()
const router = useRouter()

const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
const results = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const searched = ref(false)
const places = ref([])

async function submit() {
  const q = keyword.value.trim()
  if (!q) return
  loading.value = true
  error.value = ''
  searched.value = true
  try {
    router.replace({ path: '/search', query: { q } })
    const [page, placePage] = await Promise.all([
      search({ q, pageSize: 10 }),
      getTopics({ type: 'PLACE', q, pageSize: 6 }).catch(() => ({ list: [] })),
    ])
    results.value = page.list
    total.value = page.total
    places.value = placePage.list || []
  } catch (err) {
    error.value = err.message || '搜索失败'
    results.value = []
    total.value = 0
    places.value = []
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

      <p v-if="!searched" class="muted-text">输入关键词搜帖子或地点，比如「教三」「食堂」「插座」。</p>
      <p v-if="loading" class="muted-text">正在搜索…</p>
      <p v-else-if="error" class="muted-text">{{ error }}</p>
      <p v-else-if="searched && !results.length && !places.length" class="muted-text">没有找到相关内容。</p>
      <p v-else-if="searched" class="muted-text">共 {{ total }} 条帖子 · {{ places.length }} 个地点</p>

      <div v-if="places.length" class="search-places">
        <h3 class="search-section">地点</h3>
        <div class="place-grid">
          <RouterLink v-for="place in places" :key="place.id" :to="`/topics/${place.id}`" class="place-card">
            <span class="place-name">{{ place.name }}</span>
            <span class="place-meta">{{ place.category || '未分类' }} · {{ place.postCount }} 条内容</span>
          </RouterLink>
        </div>
      </div>

      <h3 v-if="results.length" class="search-section">帖子</h3>
      <div v-if="results.length" class="search-results">
        <PostCard v-for="post in results" :key="post.id" :post="post" />
      </div>
    </div>
  </div>
</template>
