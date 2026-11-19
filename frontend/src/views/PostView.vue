<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { COMMENTS_READY, getComments, getPost } from '../api/post'
import StateBlock from '../components/StateBlock.vue'
import TopicChip from '../components/TopicChip.vue'
import { postDisplay } from '../mock/display'
import { formatCount } from '../utils/number'
import { formatDayTime, formatRelativeTime } from '../utils/time'

const route = useRoute()
const post = ref(null)
const comments = ref([])
const loading = ref(true)
const error = ref('')
const missing = ref(false)

async function load() {
  const requestedId = route.params.id
  loading.value = true
  error.value = ''
  missing.value = false
  comments.value = []
  try {
    post.value = await getPost(requestedId)
    if (COMMENTS_READY) getComments(requestedId, { pageSize: 20 })
      .then((data) => {
        // 页面已经切到别的帖子时丢掉旧响应
        if (route.params.id === requestedId) comments.value = data.list
      })
      .catch(() => {
        // 评论是 P1 能力，取不到时帖子正文照常看
      })
  } catch (err) {
    error.value = err.message || '加载失败'
    missing.value = err.code === 404 || err.httpStatus === 404
  } finally {
    loading.value = false
  }
}

const display = computed(() => postDisplay[post.value?.id] || {})

const paragraphs = computed(() =>
  String(post.value?.content || '')
    .split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean),
)

const places = computed(() =>
  [...(post.value?.topics || [])]
    .filter((topic) => topic.type === 'PLACE')
    .sort((a, b) => a.sortNo - b.sortNo),
)

const related = computed(() => display.value.related || [])
const authorInitial = computed(() => (post.value?.author?.nickname || '?').slice(0, 1))

function stopLabel(index) {
  if (display.value.stopLabels?.[index]) return display.value.stopLabels[index]
  if (places.value.length === 1) return '地点'
  if (index === 0) return '起点'
  if (index === places.value.length - 1) return '终点'
  return '途经'
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

  <div v-else-if="post" class="page page-two">
    <main class="card article">
      <h1>{{ post.title }}</h1>

      <div class="author">
        <span class="avatar">{{ authorInitial }}</span>
        <div class="author-info">
          <span class="name">{{ post.author.nickname }}</span>
          <span class="t">{{ formatDayTime(post.createdTime) }} · 浏览 {{ formatCount(post.counts.view) }}</span>
        </div>
        <button type="button" class="follow" disabled title="P1 上线">关注</button>
      </div>

      <template v-for="(paragraph, index) in paragraphs" :key="index">
        <p>{{ paragraph }}</p>
        <div v-if="display.tallImage && index === 1" class="thumb tall">图片</div>
      </template>

      <div class="chips">
        <TopicChip v-for="topic in post.topics" :key="topic.id" :topic="topic" />
      </div>

      <div class="actions">
        <button type="button" disabled title="P1 上线">点赞 {{ post.counts.like }}</button>
        <button type="button" disabled title="P1 上线">收藏 {{ post.counts.collect }}</button>
        <button type="button" disabled title="P1 上线">评论 {{ post.counts.comment }}</button>
        <button type="button" disabled title="P1 上线">分享</button>
      </div>

      <section class="comments">
        <h2 class="comments-title">评论</h2>
        <p v-if="!comments.length" class="muted-text">还没有评论，说点什么？</p>
        <div v-for="comment in comments" :key="comment.id" class="comment">
          <span class="avatar small">{{ comment.author.nickname.slice(0, 1) }}</span>
          <div class="comment-body">
            <p class="who"><b>{{ comment.author.nickname }}</b> · {{ formatRelativeTime(comment.createdTime) }}</p>
            <p>{{ comment.content }}</p>
          </div>
        </div>
      </section>
    </main>

    <aside class="side">
      <section v-if="places.length" class="card">
        <h3>这篇帖子串了 {{ places.length }} 个地点</h3>
        <ul class="route-line">
          <li v-for="(place, index) in places" :key="place.id">
            <b>{{ place.name }}</b>{{ stopLabel(index) }}
          </li>
        </ul>
        <template v-if="display.routeSummary">
          <p class="route-summary">{{ display.routeSummary }}</p>
          <button type="button" class="btn primary block" disabled title="P1 上线">按这个顺序走</button>
        </template>
      </section>

      <section v-if="related.length" class="card">
        <h3>同类内容</h3>
        <div v-for="title in related" :key="title" class="related-row">
          <span class="rank">·</span>
          <span>{{ title }}</span>
        </div>
      </section>
    </aside>
  </div>
</template>
