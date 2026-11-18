<script setup>
import { computed, onMounted, ref } from 'vue'
import { getPosts } from '../api/post'
import { getTopicStats, getTopics } from '../api/topic'
import PostCard from '../components/PostCard.vue'
import StateBlock from '../components/StateBlock.vue'
import { categoryTotal, dailyRoute, followedPlaces } from '../mock/display'
import { isLoggedIn } from '../store/user'

const feeds = [
  { key: 'recommend', label: '推荐' },
  { key: 'latest', label: '最新' },
  { key: 'following', label: '关注' },
]

const feed = ref('recommend')
const posts = ref([])
const postTotal = ref(0)
const categoryCounts = ref({})
const categories = ref([])
const places = ref([])
const loading = ref(true)
const error = ref('')

async function loadPosts() {
  loading.value = true
  error.value = ''
  // 关注流要登录，游客直接给登录入口，不发请求
  if (feed.value === 'following' && !isLoggedIn()) {
    posts.value = []
    loading.value = false
    return
  }
  try {
    const data = await getPosts({ feed: feed.value, pageSize: 10 })
    posts.value = data.list
    postTotal.value = data.total
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
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
  } catch {
    // 侧栏失败不挡信息流，请求层已经统一提示
  }
}

function switchFeed(key) {
  if (feed.value === key) return
  feed.value = key
  loadPosts()
}

const placeTotal = computed(() => {
  const values = Object.values(categoryCounts.value)
  return values.length ? values.reduce((sum, value) => sum + value, 0) : categoryTotal
})

const hottest = computed(() => [...places.value].sort((a, b) => b.heat - a.heat).slice(0, 4))

onMounted(() => {
  loadPosts()
  loadTopics()
})
</script>

<template>
  <div class="page page-three">
    <aside class="rail">
      <h3>逛什么</h3>
      <ul>
        <li class="on">
          <span>全部</span>
          <span class="n">{{ placeTotal }}</span>
        </li>
        <li v-for="category in categories" :key="category.id">
          <span>{{ category.name }}</span>
          <span class="n">{{ categoryCounts[category.name] ?? category.postCount }}</span>
        </li>
      </ul>

      <h3>我关注的</h3>
      <ul>
        <li v-for="place in followedPlaces" :key="place.id">
          <span>{{ place.name }}</span>
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
        <PostCard v-for="post in posts" :key="post.id" :post="post" variant="home" />
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
        <ul class="route-line">
          <li v-for="step in dailyRoute.steps" :key="step.place">
            <b>{{ step.place }}</b>{{ step.note }}
          </li>
        </ul>
        <button type="button" class="btn block" disabled title="P1 上线">按这条走</button>
      </section>
    </aside>
  </div>
</template>
