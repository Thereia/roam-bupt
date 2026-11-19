<script setup>
import { onMounted, ref } from 'vue'
import { addSensitiveWord, getSensitiveWords, removeSensitiveWord } from '../../api/admin'

const words = ref([])
const total = ref(0)
const word = ref('')
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await getSensitiveWords({ pageSize: 50 })
    words.value = page.list
    total.value = page.total
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function add() {
  const value = word.value.trim()
  if (!value) return
  try {
    await addSensitiveWord(value)
    word.value = ''
    await load()
  } catch (err) {
    error.value = err.message || '添加失败'
  }
}

async function remove(item) {
  try {
    await removeSensitiveWord(item.id)
    await load()
  } catch (err) {
    error.value = err.message || '删除失败'
  }
}

onMounted(load)
</script>

<template>
  <div class="card">
    <div class="admin-head">
      <h1 class="section-title">敏感词</h1>
      <form class="admin-search" @submit.prevent="add">
        <input v-model="word" maxlength="64" placeholder="输入要拦截的词" />
        <button type="submit">添加</button>
      </form>
      <span class="muted-text">共 {{ total }} 个</span>
    </div>

    <p v-if="error" class="form-error">{{ error }}</p>
    <p v-if="loading" class="muted-text">正在加载…</p>
    <p v-else-if="!words.length" class="muted-text">还没有敏感词。</p>

    <ul v-else class="word-list">
      <li v-for="item in words" :key="item.id" class="word-item">
        <span>{{ item.word }}</span>
        <button type="button" class="danger" @click="remove(item)">删除</button>
      </li>
    </ul>
  </div>
</template>
