<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTopic, getTopicPosts } from '../api/topic'
import { getRatings, submitRating, removeRating } from '../api/rating'
import { uploadImage } from '../api/file'
import { isLoggedIn } from '../store/user'
import { formatDayTime } from '../utils/time'
import PostCard from '../components/PostCard.vue'
import StateBlock from '../components/StateBlock.vue'

const route = useRoute()
const router = useRouter()
const topic = ref(null)
const posts = ref([])
const rating = ref(null)
const reviews = ref([])
const reviewTotal = ref(0)
const reviewPage = ref(1)
const reviewPageSize = 10
const reviewsLoading = ref(false)
const reviewsError = ref('')
const form = ref({ score: 0, review: '', images: [] })
const formBusy = ref(false)
const ratingBusy = ref(false)
const uploading = ref(false)
const formError = ref('')
const formEl = ref(null)
const tab = ref('posts')
const loading = ref(true)
const error = ref('')
const missing = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  missing.value = false
  formError.value = ''
  resetForm()
  try {
    const [detail, list] = await Promise.all([
      getTopic(route.params.id),
      getTopicPosts(route.params.id, { sort: 'latest', pageSize: 10 }),
    ])
    topic.value = detail
    posts.value = list.list
    // 详情里的 rating 是 {avg, displayAvg, count}，先转成统一形状，避免模板读到 undefined
    rating.value = detail.rating
      ? { average: detail.rating.displayAvg, count: detail.rating.count, myScore: null }
      : null
    reviews.value = []
    reviewTotal.value = 0
    reviewPage.value = 1
    if (detail.type === 'PLACE') {
      loadFeed().catch(() => {})
    }
  } catch (err) {
    error.value = err.message || '加载失败'
    missing.value = err.code === 404 || err.httpStatus === 404
  } finally {
    loading.value = false
  }
}

async function loadFeed(append = false) {
  if (!topic.value || topic.value.type !== 'PLACE') return
  reviewsLoading.value = true
  reviewsError.value = ''
  try {
    const data = await getRatings(topic.value.id, {
      page: append ? reviewPage.value + 1 : 1,
      pageSize: reviewPageSize,
    })
    rating.value = data.summary
    reviewTotal.value = data.reviews.total
    reviewPage.value = data.reviews.page
    reviews.value = append ? [...reviews.value, ...data.reviews.list] : data.reviews.list
    if (!append) fillMine()
  } catch (err) {
    reviewsError.value = err.message || '加载评价失败'
  } finally {
    reviewsLoading.value = false
  }
}

const sub = computed(
  () => [topic.value?.category, `${topic.value?.postCount || 0} 条内容`].filter(Boolean).join(' · '),
)
const facts = computed(() => {
  const rows = [['分类', topic.value?.category || '未分类']]
  if (topic.value?.heat != null) rows.push(['热度', String(topic.value.heat)])
  if (topic.value?.nodeId) rows.push(['路线', '支持'])
  return rows
})
// 优先用评分接口的最新值，避免打完分顶部不变
const score = computed(() => {
  const value = rating.value?.average ?? topic.value?.rating?.displayAvg ?? 0
  return Number(value).toFixed(1)
})
const scoreCount = computed(() => rating.value?.count ?? topic.value?.rating?.count ?? 0)
const myReview = computed(() => reviews.value.find((item) => item.mine) || null)

function initial(name) {
  return (name || '?').slice(0, 1)
}

function barWidth(star) {
  const total = rating.value?.count || 0
  const count = rating.value?.distribution?.[star - 1] || 0
  return total ? `${Math.round((count / total) * 100)}%` : '0%'
}

function resetForm() {
  form.value = { score: 0, review: '', images: [] }
  formError.value = ''
}

function fillMine() {
  const mine = myReview.value
  form.value = mine
    ? {
        score: mine.score,
        review: mine.review || '',
        images: (mine.images || []).map((url) => ({ url })),
      }
    : { score: 0, review: '', images: [] }
  formError.value = ''
}

async function onFiles(event) {
  const files = Array.from(event.target.files || [])
  const remain = 3 - form.value.images.length
  if (!files.length) return
  if (remain <= 0) {
    formError.value = '最多上传 3 张图'
    event.target.value = ''
    return
  }
  uploading.value = true
  formError.value = ''
  try {
    for (const file of files.slice(0, remain)) {
      const stored = await uploadImage(file)
      form.value.images.push({ url: stored.url })
    }
    if (files.length > remain) formError.value = '最多上传 3 张图'
  } catch (err) {
    formError.value = err.message || '上传失败'
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

function removeImage(index) {
  form.value.images.splice(index, 1)
}

async function submitReview() {
  if (!isLoggedIn()) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (!form.value.score) {
    formError.value = '请先选择星级'
    return
  }
  if (form.value.review.trim().length > 200) {
    formError.value = '评价最多 200 字'
    return
  }
  if (form.value.images.length > 3) {
    formError.value = '最多上传 3 张图'
    return
  }
  if (formBusy.value) return
  formBusy.value = true
  formError.value = ''
  try {
    await submitRating(topic.value.id, {
      score: form.value.score,
      review: form.value.review.trim(),
      images: form.value.images.map((image) => image.url),
    })
    await loadFeed()
  } catch (err) {
    formError.value = err.message || '提交评价失败'
  } finally {
    formBusy.value = false
  }
}

async function removeMine() {
  if (ratingBusy.value) return
  ratingBusy.value = true
  formError.value = ''
  try {
    rating.value = await removeRating(topic.value.id)
    resetForm()
    await loadFeed()
  } catch (err) {
    formError.value = err.message || '删除评价失败'
  } finally {
    ratingBusy.value = false
  }
}

function editMine() {
  fillMine()
  formEl.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  formEl.value?.querySelector('textarea')?.focus()
}

function goRating() {
  tab.value = 'rating'
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<template>
  <section v-if="loading" class="page page-one">
    <StateBlock text="正在加载" />
  </section>

  <section v-else-if="error" class="page page-one">
    <StateBlock :text="error" :action="missing ? '' : '重新加载'" @action="load">
      <RouterLink v-if="missing" to="/" class="btn">回首页</RouterLink>
    </StateBlock>
  </section>

  <template v-else-if="topic">
    <div class="topic-hero">
      <div class="cover">
        <div>
          <h1>{{ topic.name }}</h1>
          <p class="cover-sub">{{ sub }}</p>
        </div>
      </div>
      <div class="topicbar">
        <div class="score">{{ score }}<small>{{ scoreCount }} 人评分</small></div>
        <span class="divider"></span>
        <div class="facts">
          <span><b>{{ topic.postCount }}</b> 条内容</span>
          <span><b>今日 {{ topic.todayRouteCount }}</b> 人问路</span>
        </div>
        <div class="topic-actions">
          <button type="button" class="btn" @click="goRating">去评价</button>
          <RouterLink :to="`/nav?to=${topic.id}`" class="btn primary">到这里怎么走</RouterLink>
        </div>
      </div>
    </div>

    <div class="page page-two">
      <main>
        <div class="feed-head">
          <button type="button" :class="{ on: tab === 'posts' }" @click="tab = 'posts'">广场</button>
          <button type="button" :class="{ on: tab === 'rating' }" @click="tab = 'rating'">评分</button>
        </div>

        <template v-if="tab === 'posts'">
          <StateBlock v-if="!posts.length" text="这个地点还没有帖子。" />
          <PostCard v-for="post in posts" :key="post.id" :post="post" variant="topic" />
        </template>

        <section v-else class="rating-panel">
          <div class="review-summary">
            <div class="review-score">
              <b>{{ score }}</b>
              <span class="muted-text">{{ scoreCount }} 人评价</span>
            </div>
            <div class="review-dist">
              <div v-for="star in [5, 4, 3, 2, 1]" :key="star" class="rating-row">
                <span class="rating-star">{{ star }} 星</span>
                <span class="rating-bar"><i :style="{ width: barWidth(star) }"></i></span>
                <span class="muted-text">{{ rating?.distribution?.[star - 1] || 0 }}</span>
              </div>
            </div>
          </div>

          <p v-if="reviewsError" class="form-error">{{ reviewsError }}</p>
          <StateBlock v-if="!reviewsLoading && !reviews.length" text="还没有人评价，来写第一条。" />

          <div class="review-list">
            <article v-for="item in reviews" :key="item.id" class="review-item">
              <span class="avatar">
                <img
                  v-if="item.author?.avatar"
                  :src="item.author.avatar"
                  :alt="item.author.nickname"
                  class="avatar-img"
                />
                <template v-else>{{ initial(item.author?.nickname) }}</template>
              </span>
              <div class="review-body">
                <div class="review-head">
                  <b>{{ item.author?.nickname || '已注销用户' }}</b>
                  <span class="review-stars" :aria-label="`${item.score} 星`">
                    {{ '★'.repeat(item.score) }}<span class="dim">{{ '★'.repeat(5 - item.score) }}</span>
                  </span>
                  <span class="muted-text review-time">{{ formatDayTime(item.createdTime) }}</span>
                </div>
                <p v-if="item.review" class="review-text">{{ item.review }}</p>
                <div v-if="item.images?.length" class="review-images">
                  <a
                    v-for="url in item.images"
                    :key="url"
                    :href="url"
                    target="_blank"
                    rel="noopener"
                    class="review-image"
                  >
                    <img :src="url" :alt="`${item.author?.nickname || ''} 的评价图`" loading="lazy" />
                  </a>
                </div>
                <div v-if="item.mine" class="review-actions">
                  <button type="button" class="link-button" @click="editMine">修改</button>
                  <button type="button" class="link-button" :disabled="ratingBusy" @click="removeMine">删除</button>
                </div>
              </div>
            </article>
          </div>

          <div v-if="reviews.length < reviewTotal" class="review-more">
            <button type="button" class="btn" :disabled="reviewsLoading" @click="loadFeed(true)">
              {{ reviewsLoading ? '加载中…' : '加载更多评价' }}
            </button>
          </div>

          <section ref="formEl" class="review-form">
            <h3>{{ myReview ? '修改我的评价' : '写评价' }}</h3>
            <template v-if="isLoggedIn()">
              <div class="stars form-stars">
                <button
                  v-for="star in 5"
                  :key="star"
                  type="button"
                  class="star"
                  :class="{ on: form.score >= star }"
                  :aria-label="`${star} 星`"
                  :disabled="formBusy"
                  @click="form.score = star"
                >★</button>
                <span class="muted-text">{{ form.score ? `${form.score} 星` : '点星星打分' }}</span>
              </div>
              <textarea
                v-model="form.review"
                maxlength="200"
                rows="4"
                placeholder="说说这里怎么样（选填，最多 200 字）"
              ></textarea>
              <div class="review-counter muted-text">{{ form.review.length }}/200</div>
              <div class="uploads">
                <div v-for="(image, index) in form.images" :key="image.url" class="upload-preview">
                  <img :src="image.url" alt="评价图片" />
                  <button type="button" class="upload-remove" @click="removeImage(index)">移除</button>
                </div>
                <label v-if="form.images.length < 3" class="upload-add">
                  {{ uploading ? '上传中…' : '+ 图片' }}
                  <input
                    type="file"
                    accept="image/*"
                    multiple
                    hidden
                    :disabled="uploading || formBusy"
                    @change="onFiles"
                  />
                </label>
              </div>
              <p class="form-hint">最多 3 张图，单张不超过 5MB。</p>
              <p v-if="formError" class="form-error">{{ formError }}</p>
              <div class="review-form-foot">
                <button v-if="myReview" type="button" class="btn" @click="fillMine">取消修改</button>
                <button type="button" class="btn primary" :disabled="formBusy || uploading" @click="submitReview">
                  {{ formBusy ? '提交中…' : myReview ? '保存修改' : '发布评价' }}
                </button>
              </div>
            </template>
            <template v-else>
              <p class="muted-text">登录后可以给这个地点打分、写评价、传图。</p>
              <RouterLink :to="{ path: '/login', query: { redirect: route.fullPath } }" class="btn primary">
                登录后写评价
              </RouterLink>
            </template>
          </section>
        </section>
      </main>

      <aside class="side">
        <section class="card">
          <h3>地点信息</h3>
          <div v-for="fact in facts" :key="fact[0]" class="fact-line">{{ fact[0] }}<b>{{ fact[1] }}</b></div>

          <div v-if="rating" class="rating-box">
            <div class="rating-head">
              <b class="rating-avg">{{ (rating.average ?? 0).toFixed(1) }}</b>
              <span class="muted-text">分 · {{ rating.count }} 人评分</span>
            </div>
            <button type="button" class="link-button" @click="goRating">查看评价</button>
          </div>
        </section>

      </aside>
    </div>
  </template>
</template>

<style scoped>
.review-summary {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 12px 0 16px;
  border-bottom: 1px solid var(--line);
}

.review-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 88px;
}

.review-score b {
  font-size: 38px;
  line-height: 1.1;
}

.review-dist {
  flex: 1;
  min-width: 0;
}

.review-list {
  margin-top: 4px;
}

.review-item {
  display: flex;
  gap: 10px;
  padding: 14px 0;
  border-bottom: 1px solid var(--line);
}

.review-item:last-child {
  border-bottom: 0;
}

.review-body {
  flex: 1;
  min-width: 0;
}

.review-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.review-stars {
  color: #b8862b;
  font-size: 14px;
  letter-spacing: 1px;
}

.review-stars .dim {
  color: var(--line);
}

.review-time {
  margin-left: auto;
  font-size: var(--fs-sm);
}

.review-text {
  margin: 6px 0 8px;
  white-space: pre-wrap;
  word-break: break-word;
}

.review-images {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.review-image {
  display: block;
  width: 96px;
  height: 96px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.review-image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.review-actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.review-more {
  padding: 12px 0;
  text-align: center;
}

.review-form {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
}

.review-form h3 {
  margin-bottom: 8px;
}

.review-form textarea {
  width: 100%;
  min-height: 92px;
  padding: 10px 12px;
  font: inherit;
  color: var(--ink);
  background: #fbfaf7;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  resize: vertical;
}

.form-stars {
  align-items: center;
}

.review-counter {
  margin: 2px 0 10px;
  text-align: right;
  font-size: var(--fs-sm);
}

.review-form-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 10px;
}
</style>
