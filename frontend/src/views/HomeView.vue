<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPosts } from '../api/post'
import { getPostStats, getTopicStats, getTopics } from '../api/topic'
import PostCard from '../components/PostCard.vue'
import StateBlock from '../components/StateBlock.vue'
import { isLoggedIn } from '../store/user'

const feeds = [
  { key: 'recommend', label: '推荐' },
  { key: 'latest', label: '最新' },
  { key: 'following', label: '关注' },
]

const route = useRoute()
const router = useRouter()

const feed = ref('recommend')
const posts = ref([])
const postTotal = ref(0)
const page = ref(1)
const pageSize = 10
const loadingMore = ref(false)
const done = ref(false)
const sentinel = ref(null)
let observer = null

const topicId = computed(() => {
  const value = Number(route.query.topicId)
  return Number.isFinite(value) && value > 0 ? value : null
})
const topicName = computed(() => String(route.query.name || ''))
const categoryCounts = ref({})
const categoryPostCounts = ref({})
const allPostTotal = ref(0)
const categories = ref([])
const places = ref([])
const loading = ref(true)
const error = ref('')

async function loadPosts({ reset = false } = {}) {
  if (reset) {
    page.value = 1
    posts.value = []
    done.value = false
  }
  loading.value = page.value === 1
  error.value = ''
  // 关注流要登录，游客直接给登录入口，不发请求
  if (feed.value === 'following' && !isLoggedIn()) {
    posts.value = []
    loading.value = false
    done.value = true
    return
  }
  try {
    const data = await getPosts({
      feed: feed.value,
      topicId: topicId.value ?? undefined,
      page: page.value,
      pageSize,
    })
    posts.value = page.value === 1 ? data.list : [...posts.value, ...data.list]
    postTotal.value = data.total
    done.value = data.list.length === 0 || posts.value.length >= data.total
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || done.value || loading.value) return
  loadingMore.value = true
  page.value += 1
  await loadPosts()
  loadingMore.value = false
}

function clearTopic() {
  router.replace({ path: '/', query: {} })
}

async function loadTopics() {
  try {
    const [categoryPage, placePage, stats] = await Promise.all([
      getTopics({ type: 'CATEGORY' }),
      getTopics({ type: 'PLACE' }),
      getTopicStats(),
    ])
    categories.value = categoryPage.list
    places.value = placePage.list
    categoryCounts.value = Object.fromEntries((stats || []).map((item) => [item.category, item.count]))
    const postStats = await getPostStats()
    allPostTotal.value = postStats?.total ?? 0
    categoryPostCounts.value = Object.fromEntries(
      (postStats?.categories || []).map((item) => [item.category, item.count]),
    )
  } catch {
    // 侧栏失败不挡信息流，请求层已经统一提示
  }
}

function switchFeed(key) {
  if (feed.value === key) return
  feed.value = key
  loadPosts({ reset: true })
}

watch(topicId, () => loadPosts({ reset: true }))

const placeTotal = computed(() =>
  Object.values(categoryCounts.value).reduce((sum, value) => sum + value, 0),
)

const hottest = computed(() => [...places.value].sort((a, b) => b.heat - a.heat).slice(0, 4))

function initObserver() {
  if (!observer) {
    observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) loadMore()
    }, { rootMargin: '240px' })
  }
  if (sentinel.value) observer.observe(sentinel.value)
}

// sentinel 在列表渲染后才出现，出现时再挂观察
watch(sentinel, (el) => {
  if (el) initObserver()
})

onMounted(async () => {
  await loadPosts({ reset: true })
  loadTopics()
  initObserver()
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <div class="page page-three">
    <aside class="rail">
      <h3>逛什么</h3>
      <ul>
        <li :class="{ on: !topicId }">
          <RouterLink to="/"><span>全部</span><span class="n">{{ allPostTotal }}</span></RouterLink>
        </li>
        <li v-for="category in categories" :key="category.id" :class="{ on: topicId === category.id }">
          <RouterLink :to="{ path: '/', query: { topicId: category.id, name: category.name } }">
            <span>{{ category.name }}</span>
            <span class="n">{{ categoryPostCounts[category.name] ?? 0 }}</span>
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
      <StateBlock v-else-if="feed === 'following' && !isLoggedIn()" text="登录后可以看关注的人发的帖子。">
        <RouterLink to="/login" class="btn">登录</RouterLink>
      </StateBlock>
      <StateBlock
        v-else-if="!posts.length"
        :text="feed === 'following' ? '还没有关注的人发帖。' : '这里还没有帖子。'"
      />
      <div v-else>
        <div v-if="topicId" class="topic-filter">
          <span>标签：<b>{{ topicName || `#${topicId}` }}</b> · {{ postTotal }} 条</span>
          <button type="button" class="link-btn" @click="clearTopic">清除</button>
        </div>
        <PostCard v-for="post in posts" :key="post.id" :post="post" variant="home" />
        <p v-if="loadingMore" class="muted-text">正在加载更多…</p>
        <p v-else-if="!done" class="muted-text">下滑自动加载，或点
          <button type="button" class="link-btn" @click="loadMore">加载更多</button>
        </p>
        <p v-else class="muted-text">没有更多了（共 {{ postTotal }} 条）</p>
        <div ref="sentinel" class="feed-sentinel"></div>
      </div>
    </main>

    <aside class="side">
      <section class="card">
        <h3>这周人多的地方</h3>
        <div v-for="(place, index) in hottest" :key="place.id" class="hot">
          <span class="rank">{{ index + 1 }}</span>
          <span>{{ place.name }}</span>
          <span class="bar"><i :style="{ width: `${Math.min(100, place.heat)}%` }"></i></span>
          <span class="val">{{ place.heat }}</span>
        </div>
      </section>

      <section class="card">
        <h3>今天的路线</h3>
        <p class="muted-text">想从这儿走到那儿？去路线页选两个地点就能出方案。</p>
        <RouterLink to="/nav" class="btn block">规划一条路线</RouterLink>
      </section>
    </aside>
  </div>
</template>
