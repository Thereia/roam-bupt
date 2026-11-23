<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { collectPost, uncollectPost } from '../api/post'
import { isLoggedIn } from '../store/user'
import { showNotice } from '../store/notice'
import { copyText } from '../utils/clipboard'
import TopicChip from './TopicChip.vue'
import { formatRelativeTime } from '../utils/time'

const props = defineProps({
  post: {
    type: Object,
    required: true,
  },
  variant: {
    type: String,
    default: 'home',
  },
})

const hasImage = computed(() => (props.post.media?.length || 0) > 0)
const route = useRoute()
const router = useRouter()
const busy = ref(false)

async function toggleCollect() {
  if (!isLoggedIn()) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (busy.value) return
  busy.value = true
  try {
    const active = props.post.myCollected === true
    const result = active ? await uncollectPost(props.post.id) : await collectPost(props.post.id)
    props.post.myCollected = result.active
    props.post.counts.collect = result.count
  } catch (err) {
    showNotice(err.message || '操作失败', 'error')
  } finally {
    busy.value = false
  }
}

async function share() {
  const url = `${window.location.origin}/posts/${props.post.id}`
  const ok = await copyText(url)
  showNotice(ok ? '链接已复制' : url)
}
// 地点页的卡片不再重复挂当前地点，只留分类 chip
const chips = computed(() =>
  props.variant === 'topic'
    ? props.post.topics.filter((topic) => topic.type !== 'PLACE')
    : props.post.topics,
)
</script>

<template>
  <article class="post">
    <h2>
      <RouterLink :to="`/posts/${post.id}`">{{ post.title }}</RouterLink>
    </h2>
    <div class="meta">
      <span>{{ post.author.nickname }}</span>
      <i class="dot"></i>
      <span>{{ formatRelativeTime(post.createdTime) }}</span>
      <template v-if="variant === 'topic'">
        <i class="dot"></i>
        <span>{{ post.counts.like }} 赞</span>
      </template>
    </div>
    <p class="excerpt">{{ post.excerpt }}</p>
    <div v-if="hasImage" class="thumb"><img :src="post.media[0].url" :alt="post.title" /></div>
    <div class="chips">
      <TopicChip v-for="topic in chips" :key="topic.id" :topic="topic" />
    </div>
    <div v-if="variant === 'home'" class="row">
      <span>{{ post.counts.like }} 赞 · {{ post.counts.comment }} 评论</span>
      <span class="act">
        <button type="button" :disabled="busy" @click="toggleCollect">
          {{ post.myCollected ? '已收藏' : '收藏' }}
        </button>
        <button type="button" @click="share">分享</button>
      </span>
    </div>
  </article>
</template>
