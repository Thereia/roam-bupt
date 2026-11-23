<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTopic, getTopicPosts } from '../api/topic'
import { getRatings, rateTopic } from '../api/rating'
import { isLoggedIn } from '../store/user'
import PostCard from '../components/PostCard.vue'
import StateBlock from '../components/StateBlock.vue'

const route = useRoute()
const router = useRouter()
const topic = ref(null)
const posts = ref([])
const rating = ref(null)
const ratingBusy = ref(false)
const ratingError = ref('')
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
      getTopicPosts(route.params.id, { sort: 'latest', pageSize: 10 }),
    ])
    topic.value = detail
    posts.value = list.list
    // 详情里的 rating 是 {avg, displayAvg, count}，先转成统一形状，避免模板读到 undefined
    rating.value = detail.rating
      ? { average: detail.rating.displayAvg, count: detail.rating.count, myScore: null }
      : null
    if (detail.type === 'PLACE') {
      getRatings(detail.id)
        .then((summary) => { rating.value = summary })
        .catch(() => {})
    }
  } catch (err) {
    error.value = err.message || '加载失败'
    missing.value = err.code === 404 || err.httpStatus === 404
  } finally {
    loading.value = false
  }
}

const sub = computed(
  () => [topic.value?.category, `${topic.value?.postCount || 0} 条内容`].filter(Boolean).join(' · '),
)
const facts = computed(() => {
  const rows = [['分类', topic.value?.category || '未分类']]
  if (topic.value?.heat != null) rows.push(['热度', String(topic.value.heat)])
  if (topic.value?.nodeId) rows.push(['路线', '支持'])
  return rows
})
const score = computed(() => Number(topic.value?.rating?.displayAvg || 0).toFixed(1))

async function rate(score) {
  if (!isLoggedIn()) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (ratingBusy.value) return
  ratingBusy.value = true
  ratingError.value = ''
  try {
    rating.value = await rateTopic(topic.value.id, score)
  } catch (err) {
    ratingError.value = err.message || '打分失败'
  } finally {
    ratingBusy.value = false
  }
}

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
    <div class="topic-hero">
      <div class="cover">
        <div>
          <h1>{{ topic.name }}</h1>
          <p class="cover-sub">{{ sub }}</p>
        </div>
      </div>
      <div class="topicbar">
        <div class="score">{{ score }}<small>{{ topic.rating.count }} 人评分</small></div>
        <span class="divider"></span>
        <div class="facts">
          <span><b>{{ topic.postCount }}</b> 条内容</span>
          <span><b>今日 {{ topic.todayRouteCount }}</b> 人问路</span>
        </div>
        <div class="topic-actions">
          <button type="button" class="btn" disabled title="P1 上线">打分</button>
          <button type="button" class="btn primary" disabled title="P1 上线">到这里怎么走</button>
        </div>
      </div>
    </div>

    <div class="page page-two">
      <main>
        <div class="feed-head">
          <button type="button" class="on">广场</button>
          <button type="button" disabled title="P1 上线">评分</button>
        </div>
        <StateBlock v-if="!posts.length" text="这个地点还没有帖子。" />
        <PostCard v-for="post in posts" :key="post.id" :post="post" variant="topic" />
      </main>

      <aside class="side">
        <section class="card">
          <h3>地点信息</h3>
          <div v-for="fact in facts" :key="fact[0]" class="fact-line">{{ fact[0] }}<b>{{ fact[1] }}</b></div>

          <div v-if="rating" class="rating-box">
            <div class="rating-head">
              <b class="rating-avg">{{ (rating.average ?? 0).toFixed(1) }}</b>
              <span class="muted-text">分 · {{ rating.count }} 人评分</span>
            </div>
            <div class="stars">
              <button
                v-for="score in 5"
                :key="score"
                type="button"
                class="star"
                :class="{ on: (rating.myScore || 0) >= score }"
                :disabled="ratingBusy"
                @click="rate(score)"
              >★</button>
            </div>
            <p v-if="ratingError" class="form-error">{{ ratingError }}</p>
            <p v-if="!rating.myScore" class="muted-text">点星星打分，一人一次，可改。</p>
          </div>
        </section>

      </aside>
    </div>
  </template>
</template>
