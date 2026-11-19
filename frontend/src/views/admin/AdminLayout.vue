<script setup>
import { RouterLink, RouterView, useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-side">
      <h2 class="admin-title">后台</h2>
      <RouterLink to="/admin/posts" :class="{ on: route.path.startsWith('/admin/posts') }">内容审核</RouterLink>
      <RouterLink to="/admin/users" :class="{ on: route.path.startsWith('/admin/users') }">用户管理</RouterLink>
      <RouterLink to="/admin/words" :class="{ on: route.path.startsWith('/admin/words') }">敏感词</RouterLink>
    </aside>
    <section class="admin-main">
      <RouterView />
    </section>
  </div>
</template>
