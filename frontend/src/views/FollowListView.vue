<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getFollowers, getFollowing } from '../api/user'

const route = useRoute()
const users = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(true)
const error = ref('')

const isFollowers = computed(() => route.name === 'followers')
const title = computed(() => (isFollowers.value ? '粉丝' : '关注'))

async function load(target = 1) {
  loading.value = true
  error.value = ''
  try {
    const fetcher = isFollowers.value ? getFollowers : getFollowing
    const data = await fetcher(route.params.id, { page: target, pageSize })
    users.value = data.list
    total.value = data.total
    page.value = target
  } catch (err) {
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function prev() {
  if (page.value > 1) load(page.value - 1)
}

function next() {
  if (page.value * pageSize < total.value) load(page.value + 1)
}

onMounted(() => load(1))
watch(() => [route.params.id, route.name], () => load(1))
</script>

<template>
  <div class="page page-one">
    <div class="card">
      <div class="notify-head">
        <h1 class="section-title">{{ title }}</h1>
        <RouterLink :to="`/users/${route.params.id}`" class="link-button">回主页</RouterLink>
      </div>
      <p class="muted-text">共 {{ total }} 人</p>

      <p v-if="loading" class="muted-text">正在加载…</p>
      <p v-else-if="error" class="form-error">{{ error }}</p>
      <p v-else-if="!users.length" class="muted-text">
        {{ isFollowers ? '还没有粉丝。' : '还没有关注任何人。' }}
      </p>

      <ul v-else class="user-list">
        <li v-for="user in users" :key="user.id">
          <RouterLink :to="`/users/${user.id}`" class="user-row">
            <span class="avatar">
              <img v-if="user.avatar" :src="user.avatar" :alt="user.nickname" class="avatar-img" />
              <template v-else>{{ (user.nickname || '?').slice(0, 1) }}</template>
            </span>
            <span class="user-name">{{ user.nickname }}</span>
          </RouterLink>
        </li>
      </ul>

      <div v-if="total > pageSize" class="notify-pager">
        <button type="button" :disabled="page <= 1" @click="prev">上一页</button>
        <span class="muted-text">第 {{ page }} / {{ Math.ceil(total / pageSize) }} 页</span>
        <button type="button" :disabled="page * pageSize >= total" @click="next">下一页</button>
      </div>
    </div>
  </div>
</template>
