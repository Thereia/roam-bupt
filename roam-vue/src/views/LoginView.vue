<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login, register } from '../api/auth'
import { setSession } from '../store/user'

const route = useRoute()
const router = useRouter()
const mode = ref('login')
const form = reactive({ username: '', password: '', nickname: '' })
const error = ref('')
const pending = ref(false)

function validate() {
  if (mode.value === 'register') {
    const nickname = form.nickname.trim()
    if (nickname.length < 1 || nickname.length > 32) return '昵称需要 1 到 32 个字'
  }
  const username = form.username.trim()
  if (username.length < 3 || username.length > 32) return '用户名需要 3 到 32 个字符'
  if (form.password.length < 6 || form.password.length > 32) return '密码需要 6 到 32 个字符'
  return ''
}

async function submit() {
  if (pending.value) return
  error.value = validate()
  if (error.value) return

  pending.value = true
  try {
    const payload = { username: form.username.trim(), password: form.password }
    const data =
      mode.value === 'register'
        ? await register({ ...payload, nickname: form.nickname.trim() })
        : await login(payload)
    setSession(data.token, data.user)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (err) {
    error.value = err.message || '请求失败'
  } finally {
    pending.value = false
  }
}

function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  error.value = ''
}
</script>

<template>
  <section class="card auth">
    <h1>{{ mode === 'login' ? '登录' : '注册' }}</h1>

    <form @submit.prevent="submit">
      <div class="field">
        <label for="username">用户名</label>
        <input id="username" v-model="form.username" class="input" autocomplete="username" />
      </div>

      <div v-if="mode === 'register'" class="field">
        <label for="nickname">昵称</label>
        <input id="nickname" v-model="form.nickname" class="input" />
      </div>

      <div class="field">
        <label for="password">密码</label>
        <input
          id="password"
          v-model="form.password"
          class="input"
          type="password"
          :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
        />
      </div>

      <p v-if="error" class="form-error">{{ error }}</p>

      <button type="submit" class="btn primary block" :disabled="pending">
        {{ pending ? '提交中' : mode === 'login' ? '登录' : '注册' }}
      </button>
    </form>

    <p class="switch">
      {{ mode === 'login' ? '还没有账号' : '已经有账号' }}
      <button type="button" @click="switchMode">{{ mode === 'login' ? '注册' : '登录' }}</button>
    </p>
  </section>
</template>
