<script setup>
import AppLayout from './components/AppLayout.vue'
import { notice } from './api/request'
</script>

<template>
  <AppLayout>
    <RouterView />
  </AppLayout>
  <p v-if="notice.text" class="notice" :class="notice.kind" role="status">{{ notice.text }}</p>
</template>
