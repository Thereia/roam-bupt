<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => route.meta.title || '这个页面')
</script>

<template>
  <section class="placeholder">
    <h1>{{ title }}</h1>
    <p>建设中，这一期先不做。地点和帖子已经能看了。</p>
    <RouterLink to="/" class="btn">回首页</RouterLink>
  </section>
</template>
