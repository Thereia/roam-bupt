<script setup>
import { computed, onMounted, ref } from 'vue'
import { getPosts } from '../api/post'
import { getTopics } from '../api/topic'
import PostCard from '../components/PostCard.vue'
import StateBlock from '../components/StateBlock.vue'

const feeds = [
  { key: 'recommend', label: '推荐' },
  { key: 'latest', label: '最新' },
]

const feed = ref('recommend')
const posts = ref([])
const topics = ref([])
const loading = ref(true)
const error = ref('')

async function loadPosts() {
  loading.value = true
  error.value = ''
  try {
    const data = await getPosts({ feed: feed.value, pageSize: 10 })
    posts.value = data.list
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadTopics() {
  try {
    const data = await getTopics({ type: 'PLACE', sort: 'postCount' })
    topics.value = data.list
  } catch {
    // 侧栏失败不该挡住信息流，状态码提示已在请求层统一弹出
  }
}

function switchFeed(key) {
  if (feed.value === key) return
  feed.value = key
  loadPosts()
}

const rail = computed(() => [...topics.value].sort((a, b) => b.postCount - a.postCount))
const hottest = computed(() => [...topics.value].sort((a, b) => b.heat - a.heat).slice(0, 4))
const maxHeat = computed(() => Math.max(1, ...hottest.value.map((topic) => topic.heat)))

onMounted(() => {
  loadPosts()
  loadTopics()
})
</script>

<template>
  <div class="page page-three">
    <aside class="rail">
      <h3>地点</h3>
      <ul class="rail-list">
        <li v-for="topic in rail" :key="topic.id">
          <RouterLink :to="`/topics/${topic.id}`">
            <span>{{ topic.name }}</span>
            <span class="count">{{ topic.postCount }}</span>
          </RouterLink>
        </li>
      </ul>
    </aside>

    <main>
      <div class="feed-head">
        <button
          v-for="item in feeds"
          :key="item.key"
          type="button"
          :class="{ on: feed === item.key }"
          @click="switchFeed(item.key)"
        >
          {{ item.label }}
        </button>
      </div>

      <StateBlock v-if="loading" text="正在加载" />
      <StateBlock v-else-if="error" :text="error" action="重新加载" @action="loadPosts" />
      <StateBlock v-else-if="!posts.length" text="这里还没有帖子。" />
      <div v-else>
        <PostCard v-for="post in posts" :key="post.id" :post="post" />
      </div>
    </main>

    <aside class="side">
      <section class="card">
        <h3>热度排行</h3>
        <div v-for="(topic, index) in hottest" :key="topic.id" class="hot-row">
          <span class="rank">{{ index + 1 }}</span>
          <RouterLink class="hot-name" :to="`/topics/${topic.id}`">{{ topic.name }}</RouterLink>
          <span class="bar"><i :style="{ width: `${Math.round((topic.heat / maxHeat) * 100)}%` }"></i></span>
          <span class="val">{{ topic.heat }}</span>
        </div>
        <p v-if="!hottest.length" class="muted-text">还没有热度数据。</p>
      </section>
    </aside>
  </div>
</template>
