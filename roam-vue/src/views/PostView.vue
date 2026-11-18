<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getComments, getPost } from '../api/post'
import StateBlock from '../components/StateBlock.vue'
import TopicChip from '../components/TopicChip.vue'
import { formatRelativeTime } from '../utils/time'

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
    getComments(requestedId, { pageSize: 20 })
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

const authorInitial = computed(() => (post.value?.author?.nickname || '?').slice(0, 1))

function placeLabel(index) {
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
    <main>
      <article class="card article">
        <h1>{{ post.title }}</h1>

        <div class="author">
          <span class="avatar">{{ authorInitial }}</span>
          <div class="author-info">
            <span class="name">{{ post.author.nickname }}</span>
            <span class="t">{{ formatRelativeTime(post.createdTime) }} · 浏览 {{ post.counts.view }}</span>
          </div>
        </div>

        <p v-for="(paragraph, index) in paragraphs" :key="index">{{ paragraph }}</p>

        <div class="chips">
          <TopicChip v-for="topic in post.topics" :key="topic.id" :topic="topic" />
        </div>

        <div class="actions">
          <span>点赞 {{ post.counts.like }}</span>
          <span>收藏 {{ post.counts.collect }}</span>
          <span>评论 {{ post.counts.comment }}</span>
        </div>

        <section class="comments">
          <h2 class="section-title">评论</h2>
          <p v-if="!comments.length" class="muted-text">还没有评论。</p>
          <div v-for="comment in comments" :key="comment.id" class="comment">
            <span class="avatar small">{{ comment.author.nickname.slice(0, 1) }}</span>
            <div class="comment-body">
              <p class="who"><b>{{ comment.author.nickname }}</b> · {{ formatRelativeTime(comment.createdTime) }}</p>
              <p>{{ comment.content }}</p>
            </div>
          </div>
        </section>
      </article>
    </main>

    <aside class="side">
      <section v-if="places.length" class="card">
        <h3>帖子里的地点顺序</h3>
        <ol class="route-line">
          <li v-for="(place, index) in places" :key="place.id">
            <b>{{ place.name }}</b>
            <span class="stop">{{ placeLabel(index) }}</span>
          </li>
        </ol>
      </section>
    </aside>
  </div>
</template>
