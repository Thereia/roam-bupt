<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createPost } from '../api/post'
import { getTopicSuggest, getTopics } from '../api/topic'
import { uploadImage } from '../api/file'

const router = useRouter()

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
  if (!selected.value.some((item) => item.type === 'PLACE')) {
    error.value = '至少挂一个地点'
    return
  }
  submitting.value = true
  try {
    const result = await createPost({
      title: title.value.trim(),
      content: content.value,
      topics: selected.value.map((item, index) => ({ topicId: item.id, sortNo: index })),
      media: images.value.map((item, index) => ({ type: 'IMAGE', url: item.url, sortNo: index })),
    })
    router.push(`/posts/${result.id}`)
  } catch (err) {
    error.value = err.message || '发布失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page page-one">
    <div class="card form">
      <h1 class="form-title">写点什么</h1>

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

      <label>挂在哪些主题上</label>
      <input v-model="keyword" placeholder="搜地点，比如「教三」「图书馆」" @input="onKeywordInput" />
      <div v-if="suggestions.length" class="suggest-list">
        <button v-for="item in suggestions" :key="item.id" type="button" class="suggest-item" @click="pick(item)">
          {{ item.name }} · {{ item.type === 'PLACE' ? '地点' : '分类' }}
        </button>
      </div>

      <div class="chips">
        <span v-for="topic in selected" :key="topic.id" class="chip solid">
          {{ topic.name }}<button type="button" class="chip-remove" @click="remove(topic.id)">×</button>
        </span>
      </div>

      <div class="form-hint">地点主题的顺序就是路线顺序；分类主题不参与排序。</div>

      <p v-if="error" class="form-error">{{ error }}</p>

      <div class="form-foot">
        <span class="muted-text">发布后所有人可见</span>
        <button type="button" class="btn-primary" :disabled="submitting" @click="submit">
          {{ submitting ? '发布中…' : '发布' }}
        </button>
      </div>
    </div>
  </div>
</template>
