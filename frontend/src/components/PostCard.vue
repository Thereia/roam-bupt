<script setup>
import { computed } from 'vue'
import TopicChip from './TopicChip.vue'
import { postDisplay } from '../mock/display'
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

const display = computed(() => postDisplay[props.post.id] || {})
const actions = computed(() => display.value.actions || ['收藏', '分享'])
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
    <div v-if="display.hasImage" class="thumb">图片</div>
    <div class="chips">
      <TopicChip v-for="topic in chips" :key="topic.id" :topic="topic" />
    </div>
    <div v-if="variant === 'home'" class="row">
      <span>{{ post.counts.like }} 赞 · {{ post.counts.comment }} 评论</span>
      <span class="act">
        <button v-for="label in actions" :key="label" type="button" disabled title="P1 上线">{{ label }}</button>
      </span>
    </div>
  </article>
</template>
