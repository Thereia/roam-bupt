<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getTopic, getTopicPosts } from '../api/topic'
import PostCard from '../components/PostCard.vue'
import StateBlock from '../components/StateBlock.vue'

const route = useRoute()
const topic = ref(null)
const posts = ref([])
const sort = ref('latest')
const loading = ref(true)
const error = ref('')
const missing = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  missing.value = false
  try {
    const [detail, list] = await Promise.all([
      getTopic(route.params.id),
      getTopicPosts(route.params.id, { sort: sort.value, pageSize: 10 }),
    ])
    topic.value = detail
    posts.value = list.list
  } catch (err) {
    error.value = err.message || '加载失败'
    missing.value = err.code === 404 || err.httpStatus === 404
  } finally {
    loading.value = false
  }
}

async function loadPosts() {
  try {
    const list = await getTopicPosts(route.params.id, { sort: sort.value, pageSize: 10 })
    posts.value = list.list
  } catch (err) {
    error.value = err.message || '加载失败'
  }
}

function switchSort(key) {
  if (sort.value === key) return
  sort.value = key
  loadPosts()
}

const score = computed(() => {
  const rating = topic.value?.rating
  return rating ? Number(rating.displayAvg).toFixed(1) : ''
})

onMounted(load)
watch(() => route.params.id, load)
</script>

<template>
  <section v-if="loading" class="page page-one">
    <StateBlock text="正在加载" />
  </section>

  <section v-else-if="error" class="page page-one">
    <StateBlock :text="error" :action="missing ? '' : '重新加载'" @action="load">
      <RouterLink v-if="missing" to="/" class="btn">回首页</RouterLink>
    </StateBlock>
  </section>

  <template v-else-if="topic">
    <header class="topic-head">
      <div class="cover">
        <h1>{{ topic.name }}</h1>
        <p class="cover-sub">{{ topic.category }} · {{ topic.postCount }} 条内容</p>
      </div>
      <div class="topicbar">
        <div class="score">{{ score }}<small>{{ topic.rating.count }} 人评分</small></div>
        <span class="divider"></span>
        <div class="facts">
          <span><b>今日 {{ topic.todayRouteCount }}</b> 人问路</span>
        </div>
      </div>
    </header>

    <div class="page page-two">
      <main>
        <div class="feed-head">
          <button type="button" :class="{ on: sort === 'latest' }" @click="switchSort('latest')">最新</button>
          <button type="button" :class="{ on: sort === 'hot' }" @click="switchSort('hot')">热门</button>
        </div>
        <StateBlock v-if="!posts.length" text="这个地点还没有帖子。" />
        <PostCard v-for="post in posts" :key="post.id" :post="post" />
      </main>

      <aside class="side">
        <section v-if="topic.description" class="card">
          <h3>说明</h3>
          <p class="topic-desc">{{ topic.description }}</p>
        </section>
      </aside>
    </div>
  </template>
</template>
