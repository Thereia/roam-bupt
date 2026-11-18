<script setup>
import TopicChip from './TopicChip.vue'
import { formatRelativeTime } from '../utils/time'

defineProps({
  post: {
    type: Object,
    required: true,
  },
})
</script>

<template>
  <article class="post">
    <h2>
      <RouterLink :to="`/posts/${post.id}`">{{ post.title }}</RouterLink>
    </h2>
    <div class="meta">
      <span>{{ post.author.nickname }}</span>
      <span class="dot"></span>
      <span>{{ formatRelativeTime(post.createdTime) }}</span>
    </div>
    <p class="excerpt">{{ post.excerpt }}</p>
    <div class="chips">
      <TopicChip v-for="topic in post.topics" :key="topic.id" :topic="topic" />
    </div>
    <div class="post-foot">
      <span>{{ post.counts.like }} 赞 · {{ post.counts.comment }} 评论</span>
    </div>
  </article>
</template>
