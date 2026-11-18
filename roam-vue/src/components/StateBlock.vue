<script setup>
defineProps({
  text: {
    type: String,
    required: true,
  },
  action: {
    type: String,
    default: '',
  },
})

defineEmits(['action'])
</script>

<template>
  <div class="state-block">
    <p>{{ text }}</p>
    <button v-if="action" type="button" class="btn" @click="$emit('action')">{{ action }}</button>
    <slot />
  </div>
</template>
