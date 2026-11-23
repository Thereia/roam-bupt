<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  COMMENTS_READY, collectPost, createComment, deleteComment, deletePost, getComments, getPost, likePost,
  uncollectPost, unlikePost,
} from '../api/post'
import StateBlock from '../components/StateBlock.vue'
import TopicChip from '../components/TopicChip.vue'
import ImageLightbox from '../components/ImageLightbox.vue'
import { isLoggedIn, userStore } from '../store/user'
import { showNotice } from '../store/notice'
import { copyText } from '../utils/clipboard'
import { getPostRoute } from '../api/space'
import { formatCount } from '../utils/number'
import { formatDayTime, formatRelativeTime } from '../utils/time'

const route = useRoute()
const router = useRouter()
const post = ref(null)
const comments = ref([])
const routePlan = ref(null)
const routeError = ref('')
const routeLoading = ref(false)
const commentText = ref('')
const commenting = ref(false)
const commentError = ref('')
const loading = ref(true)
const error = ref('')
const missing = ref(false)
const galleryIndex = ref(-1)

const galleryImages = computed(() => (post.value?.media || []).map((image) => image.url))

function openGallery(index) {
  galleryIndex.value = index
}

function scrollToComments() {
  document.querySelector('.comments')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const canEdit = computed(() => {
  const me = userStore.user
  return !!(me && post.value?.author && me.id === post.value.author.id)
})

async function editPost() {
  router.push({ path: '/publish', query: { edit: String(post.value.id) } })
}

async function removePost() {
  if (!window.confirm('删除后不可恢复，确定删除这篇帖子？')) return
  try {
    await deletePost(post.value.id)
    showNotice('帖子已删除')
    router.push('/')
  } catch (err) {
    showNotice(err.message || '删除失败', 'error')
  }
}

async function removeComment(comment) {
  if (!window.confirm('确定删除这条评论？')) return
  try {
    await deleteComment(comment.id)
    comments.value = comments.value.filter((item) => item.id !== comment.id)
    if (post.value?.counts) post.value.counts.comment = Math.max(0, post.value.counts.comment - 1)
  } catch (err) {
    showNotice(err.message || '删除失败', 'error')
  }
}

function canRemoveComment(comment) {
  const me = userStore.user
  if (!me) return false
  return me.id === comment.author.id || me.role === 'ADMIN'
}

async function sharePost() {
  const url = `${window.location.origin}/posts/${post.value.id}`
  const ok = await copyText(url)
  showNotice(ok ? '链接已复制' : url)
}

async function loadRoute() {
  routeLoading.value = true
  routeError.value = ''
  try {
    routePlan.value = await getPostRoute(post.value.id)
  } catch (err) {
    routeError.value = err.message || '这条路暂时算不出来'
  } finally {
    routeLoading.value = false
  }
}

async function submitComment() {
  const text = commentText.value.trim()
  if (!text) return
  if (!isLoggedIn()) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  commenting.value = true
  commentError.value = ''
  try {
    const created = await createComment(post.value.id, text)
    comments.value = [
      {
        id: created.id,
        postId: post.value.id,
        content: text,
        author: userStore.user,
        createdTime: new Date().toISOString(),
        replyCount: 0,
      },
      ...comments.value,
    ]
    post.value = {
      ...post.value,
      counts: { ...post.value.counts, comment: (post.value.counts.comment || 0) + 1 },
    }
    commentText.value = ''
  } catch (err) {
    commentError.value = err.message || '发表失败'
  } finally {
    commenting.value = false
  }
}

async function toggleLike() {
  if (!isLoggedIn()) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const id = post.value.id
  const result = post.value.myLiked ? await unlikePost(id) : await likePost(id)
  post.value = {
    ...post.value,
    myLiked: result.active,
    counts: { ...post.value.counts, like: result.count },
  }
}

async function toggleCollect() {
  if (!isLoggedIn()) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const id = post.value.id
  const result = post.value.myCollected ? await uncollectPost(id) : await collectPost(id)
  post.value = {
    ...post.value,
    myCollected: result.active,
    counts: { ...post.value.counts, collect: result.count },
  }
}

async function load() {
  const requestedId = route.params.id
  galleryIndex.value = -1
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

function stopLabel(index) {
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
        <RouterLink v-if="post.author" :to="`/users/${post.author.id}`" class="follow">查看主页</RouterLink>
      </div>

      <template v-for="(paragraph, index) in paragraphs" :key="index">
        <p>{{ paragraph }}</p>
      </template>

      <div v-if="post.media?.length" class="post-gallery">
        <button
          v-for="(image, index) in post.media"
          :key="`${image.url}-${index}`"
          type="button"
          class="gallery-item"
          :aria-label="`查看第 ${index + 1} 张图`"
          @click="openGallery(index)"
        >
          <img :src="image.url" :alt="`${post.title} 配图 ${index + 1}`" loading="lazy" />
        </button>
      </div>

      <div class="chips">
        <TopicChip v-for="topic in post.topics" :key="topic.id" :topic="topic" />
      </div>

      <p v-if="post.status && post.status !== 'ONLINE'" class="review-hint">
        这篇帖子还在审核中，通过后其他人才能看到，暂时不能点赞和评论。
      </p>

      <div v-if="canEdit" class="author-actions">
        <button type="button" class="btn" @click="editPost">编辑</button>
        <button type="button" class="btn" @click="removePost">删除</button>
      </div>

      <div v-if="post.status === 'ONLINE'" class="actions">
        <button type="button" :class="{ on: post.myLiked }" @click="toggleLike">
          {{ post.myLiked ? '已赞' : '点赞' }} {{ post.counts.like }}
        </button>
        <button type="button" :class="{ on: post.myCollected }" @click="toggleCollect">
          {{ post.myCollected ? '已收藏' : '收藏' }} {{ post.counts.collect }}
        </button>
        <button type="button" @click="scrollToComments">评论 {{ post.counts.comment }}</button>
        <button type="button" @click="sharePost">分享</button>
      </div>

      <section class="comments">
        <h2 class="comments-title">评论</h2>

        <form v-if="post.status === 'ONLINE'" class="comment-form" @submit.prevent="submitComment">
          <textarea
            v-model="commentText"
            maxlength="1000"
            :placeholder="isLoggedIn() ? '说点什么，对后来的人有用' : '登录后可以评论'"
            rows="2"
          ></textarea>
          <div class="comment-form-foot">
            <span v-if="commentError" class="form-error">{{ commentError }}</span>
            <button type="submit" class="btn primary" :disabled="commenting || !commentText.trim()">
              {{ commenting ? '发表中…' : '发表' }}
            </button>
          </div>
        </form>

        <p v-if="!comments.length" class="muted-text">还没有评论，说点什么？</p>
        <div v-for="comment in comments" :key="comment.id" class="comment">
          <span class="avatar small">{{ comment.author.nickname.slice(0, 1) }}</span>
          <div class="comment-body">
            <p class="who">
              <b>{{ comment.author.nickname }}</b> · {{ formatRelativeTime(comment.createdTime) }}
              <button v-if="canRemoveComment(comment)" type="button" class="link-button" @click="removeComment(comment)">删除</button>
            </p>
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
        <button type="button" class="btn primary block" :disabled="routeLoading" @click="loadRoute">
          {{ routeLoading ? '正在算路…' : '按这个顺序走' }}
        </button>
        <p v-if="routeError" class="form-error">{{ routeError }}</p>
        <div v-if="routePlan" class="route-result">
          <p class="route-summary">
            全程 {{ routePlan.totalDistanceMeters }} 米 · 约 {{ Math.round(routePlan.totalTimeSeconds / 60) }} 分钟
          </p>
          <div v-for="(segment, index) in routePlan.segments" :key="index" class="plan-segment">
            <span class="dot" :class="{ busy: segment.congestion >= 1.5 }"></span>
            <span>{{ segment.fromName }} → {{ segment.toName }}</span>
            <span class="muted-text">{{ segment.distanceMeters }}m</span>
          </div>
        </div>
      </section>
    </aside>
  </div>

  <ImageLightbox
    v-if="galleryIndex >= 0 && post"
    :images="galleryImages"
    :index="galleryIndex"
    @close="galleryIndex = -1"
  />
</template>
