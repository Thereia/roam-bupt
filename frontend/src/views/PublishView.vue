<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createPost, getPost, updatePost } from '../api/post'
import { getTopicSuggest, getTopics } from '../api/topic'
import { uploadImage } from '../api/file'

const router = useRouter()
const route = useRoute()

const editId = ref(typeof route.query.edit === 'string' ? route.query.edit : '')
const editing = ref(!!editId.value)

const title = ref('')
const content = ref('')
const images = ref([])
const uploading = ref(false)
const submitting = ref(false)
const error = ref('')

const categories = ref([])
const keyword = ref('')
const suggestions = ref([])
const selected = ref([])
let suggestTimer = null

onMounted(async () => {
  try {
    const page = await getTopics({ type: 'CATEGORY' })
    categories.value = page.list
  } catch {
    categories.value = []
  }
  if (editing.value) {
    try {
      const post = await getPost(editId.value)
      title.value = post.title
      content.value = post.content
      selected.value = (post.topics || []).map((t) => ({ ...t }))
      images.value = (post.media || []).map((m) => ({ url: m.url }))
    } catch (err) {
      error.value = err.message || '加载原帖失败'
    }
  }
})

function onKeywordInput() {
  clearTimeout(suggestTimer)
  const q = keyword.value.trim()
  if (!q) {
    suggestions.value = []
    return
  }
  suggestTimer = setTimeout(async () => {
    try {
      suggestions.value = await getTopicSuggest({ q, limit: 8 })
    } catch {
      suggestions.value = []
    }
  }, 200)
}

function pick(topic) {
  if (selected.value.some((item) => item.id === topic.id)) return
  if (selected.value.length >= 9) {
    error.value = '最多挂 9 个主题'
    return
  }
  selected.value.push({ ...topic, sortNo: selected.value.length })
  keyword.value = ''
  suggestions.value = []
}

function remove(id) {
  selected.value = selected.value.filter((item) => item.id !== id)
}

async function onFiles(event) {
  const files = Array.from(event.target.files || [])
  if (!files.length) return
  uploading.value = true
  error.value = ''
  try {
    for (const file of files) {
      const stored = await uploadImage(file)
      images.value.push(stored)
    }
  } catch (err) {
    error.value = err.message || '上传失败'
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

watch([title, content, selected], () => {
  if (error.value) error.value = ''
})

async function saveDraft() {
  error.value = ''
  if (!title.value.trim()) {
    error.value = '草稿也要先写标题'
    return
  }
  submitting.value = true
  try {
    const result = await createPost({
      title: title.value.trim(),
      content: content.value,
      status: 'DRAFT',
      topics: selected.value.map((item, index) => ({
        topicId: item.id,
        sortNo: item.type === 'PLACE' ? index : 0,
      })),
      media: images.value.map((item, index) => ({ type: 'IMAGE', url: item.url, sortNo: index })),
    }, { silent: true })
    router.push(`/drafts`)
    return result
  } catch (err) {
    error.value = err.message || '存草稿失败'
  } finally {
    submitting.value = false
  }
}

async function submit() {
  error.value = ''
  if (!title.value.trim()) {
    error.value = '标题不能为空'
    return
  }
  if (!content.value.trim()) {
    error.value = '正文不能为空'
    return
  }
  if (!selected.value.length) {
    error.value = '至少选一个地点或分类'
    return
  }
  submitting.value = true
  try {
    const payload = {
      title: title.value.trim(),
      content: content.value,
      topics: selected.value.map((item, index) => ({
        topicId: item.id,
        sortNo: item.type === 'PLACE' ? index : 0,
      })),
      media: images.value.map((item, index) => ({ type: 'IMAGE', url: item.url, sortNo: index })),
    }
    const result = editing.value
      ? await updatePost(editId.value, payload, { silent: true })
      : await createPost(payload, { silent: true })
    router.push(`/posts/${result.id}`)
  } catch (err) {
    error.value = err.message || (editing.value ? '保存失败' : '发布失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page page-one">
    <div class="card form">
      <h1 class="form-title">{{ editing ? '编辑帖子' : '写点什么' }}</h1>

      <label>标题</label>
      <input v-model="title" maxlength="100" placeholder="一句话说清这帖讲什么" />

      <label>正文</label>
      <textarea v-model="content" maxlength="20000" placeholder="把位置、时间、细节写清楚"></textarea>

      <label>图片</label>
      <div class="uploads">
        <div v-for="(image, index) in images" :key="image.url" class="upload-preview">
          <img :src="image.url" :alt="`图片 ${index + 1}`" />
          <button type="button" class="upload-remove" @click="images.splice(index, 1)">移除</button>
        </div>
        <label class="upload-add">
          <input type="file" accept="image/jpeg,image/png,image/webp" multiple hidden @change="onFiles" />
          {{ uploading ? '上传中…' : '+ 添加图片' }}
        </label>
      </div>

      <label>挂在哪里（选地点或分类都行；地点会决定路线顺序）</label>
      <input v-model="keyword" placeholder="搜地点，比如「教三」「图书馆」" @input="onKeywordInput" />
      <div v-if="suggestions.length" class="suggest-list">
        <button v-for="item in suggestions" :key="item.id" type="button" class="suggest-item" @click="pick(item)">
          {{ item.name }} · {{ item.type === 'PLACE' ? '地点' : '分类' }}
        </button>
      </div>

      <div class="chips">
        <span v-for="topic in selected" :key="topic.id" class="chip solid">
          <b v-if="topic.type === 'PLACE'">{{ selected.filter((t) => t.type === 'PLACE').indexOf(topic) + 1 }}.</b>
          {{ topic.name }}<button type="button" class="chip-remove" @click="remove(topic.id)">×</button>
        </span>
      </div>

      <div class="form-hint">地点会参与路线排序，分类只做归类；不选地点也能发，只是没有路线。</div>

      <p v-if="error" class="form-error">{{ error }}</p>

      <div class="form-foot">
        <span class="muted-text">{{ editing ? '保存后需要重新审核，通过前其他人看不到' : '提交后需审核，通过后所有人可见' }}</span>
        <button v-if="!editing" type="button" class="btn" :disabled="submitting" @click="saveDraft">存草稿</button>
        <button type="button" class="btn-primary" :disabled="submitting" @click="submit">
          {{ submitting ? '保存中…' : (editing ? '保存修改' : '发布') }}
        </button>
      </div>
    </div>
  </div>
</template>
