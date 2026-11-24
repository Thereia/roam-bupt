<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTopics } from '../api/topic'

const route = useRoute()
const router = useRouter()

const categories = ref([])
const places = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 24
const activeCategory = ref('')
const keyword = ref('')
const loading = ref(true)
const error = ref('')

async function loadCategories() {
  try {
    const data = await getTopics({ type: 'CATEGORY', pageSize: 50 })
    categories.value = data.list
  } catch {
    categories.value = []
  }
}

async function loadPlaces() {
  loading.value = true
  error.value = ''
  try {
    const data = await getTopics({
      type: 'PLACE',
      category: activeCategory.value || undefined,
      q: keyword.value.trim() || undefined,
      page: page.value,
      pageSize,
    })
    places.value = data.list
    total.value = data.total
  } catch (err) {
    error.value = err.message || '加载失败'
    places.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function pickCategory(name) {
  activeCategory.value = activeCategory.value === name ? '' : name
  page.value = 1
  loadPlaces()
}

function submitSearch() {
  page.value = 1
  loadPlaces()
}

function prev() {
  if (page.value > 1) {
    page.value -= 1
    loadPlaces()
  }
}

function next() {
  if (page.value * pageSize < total.value) {
    page.value += 1
    loadPlaces()
  }
}

function openPlace(id) {
  router.push(`/topics/${id}`)
}

watch(() => route.query.category, (value) => {
  activeCategory.value = typeof value === 'string' ? value : ''
  page.value = 1
  loadPlaces()
})

onMounted(async () => {
  activeCategory.value = typeof route.query.category === 'string' ? route.query.category : ''
  await Promise.all([loadCategories(), loadPlaces()])
})
</script>

<template>
  <div class="page page-one">
    <div class="card">
      <div class="topics-head">
        <h1 class="section-title">主题</h1>
        <form class="topics-search" @submit.prevent="submitSearch">
          <input v-model="keyword" type="search" placeholder="搜主题，比如「食堂」「自习」" maxlength="30" />
          <button type="submit">搜索</button>
        </form>
        <span class="muted-text">共 {{ total }} 个主题</span>
      </div>

      <div class="topics-filters">
        <button type="button" :class="{ on: !activeCategory }" @click="pickCategory('')">全部</button>
        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          :class="{ on: activeCategory === category.name }"
          @click="pickCategory(category.name)"
        >
          {{ category.name }}
        </button>
      </div>

      <p v-if="loading" class="muted-text">正在加载…</p>
      <p v-else-if="error" class="form-error">{{ error }}</p>
      <p v-else-if="!places.length" class="muted-text">没有匹配的主题。</p>

      <div v-else class="place-grid">
        <button v-for="place in places" :key="place.id" type="button" class="place-card" @click="openPlace(place.id)">
          <span class="place-name">{{ place.name }}</span>
          <span class="place-meta">{{ place.category || '未分类' }}</span>
          <span class="place-foot">
            <span>{{ place.postCount }} 条内容 </span>
            <span v-if="place.rating && place.rating.count">· {{ place.rating.displayAvg }} 分</span>
            <span v-else>· 暂无评分</span>
          </span>
        </button>
      </div>

      <div v-if="total > pageSize" class="notify-pager">
        <button type="button" :disabled="page <= 1" @click="prev">上一页</button>
        <span class="muted-text">第 {{ page }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button type="button" :disabled="page * pageSize >= total" @click="next">下一页</button>
      </div>
    </div>
  </div>
</template>
