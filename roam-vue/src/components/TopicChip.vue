<script setup>
import { computed } from 'vue'

const props = defineProps({
  topic: {
    type: Object,
    required: true,
  },
})

const isPlace = computed(() => props.topic.type === 'PLACE')
</script>

<template>
  <RouterLink v-if="isPlace" class="chip solid" :to="`/topics/${topic.id}`">{{ topic.name }}</RouterLink>
  <span v-else class="chip">{{ topic.name }}</span>
</template>
