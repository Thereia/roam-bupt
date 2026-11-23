<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getMap, planRoutes } from '../api/space'
import { getTopic, getTopicSuggest } from '../api/topic'

const route = useRoute()

const MAP_W = 748
const MAP_H = 1089

const loading = ref(true)
const error = ref('')
const nodes = ref([])
const edges = ref([])
const plans = ref([])
const selected = ref(0)
const planning = ref(false)

const from = ref(null)
const to = ref(null)
const keywordFrom = ref('')
const keywordTo = ref('')
const suggestionsFrom = ref([])
const suggestionsTo = ref([])

const nodeById = computed(() => new Map(nodes.value.map((node) => [node.id, node])))

const highlighted = computed(() => {
  const plan = plans.value[selected.value]
  if (!plan) return []
  return plan.nodeIds
    .map((id) => nodeById.value.get(id))
    .filter(Boolean)
    .map((node) => `${(node.x * MAP_W).toFixed(1)},${(node.y * MAP_H).toFixed(1)}`)
    .join(' ')
})

const canPlan = computed(() => from.value && to.value && from.value.id !== to.value.id)

const planHint = computed(() => {
  if (!from.value) return '先选起点'
  if (!to.value) return '再选终点'
  if (from.value.id === to.value.id) return '起点和终点不能是同一个地方'
  return ''
})

function displaySegments(plan) {
  const rows = []
  let junctionCount = 0
  let junctionMeters = 0
  const flush = () => {
    if (!junctionCount) return
    rows.push({
      junction: true,
      label: `穿过 ${junctionCount} 个路口`,
      distanceMeters: junctionMeters,
      congestion: 1,
    })
    junctionCount = 0
    junctionMeters = 0
  }
  for (const segment of plan.segments) {
    const isJunction = segment.fromName === '路口' || segment.toName === '路口'
    if (isJunction) {
      junctionCount += 1
      junctionMeters += segment.distanceMeters
    } else {
      flush()
      rows.push({
        junction: false,
        label: `${segment.fromName} → ${segment.toName}`,
        distanceMeters: segment.distanceMeters,
        congestion: segment.congestion,
      })
    }
  }
  flush()
  return rows
}

function congestionColor(value) {
  if (value >= 3) return '#b4453a'
  if (value >= 1.5) return '#b8862b'
  return '#4a7c59'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const map = await getMap()
    nodes.value = map.nodes
    edges.value = map.edges
  } catch (err) {
    error.value = err.message || '底图加载失败'
  } finally {
    loading.value = false
  }
}

function search(which) {
  const keyword = (which === 'from' ? keywordFrom : keywordTo).value.trim()
  if (!keyword) return
  getTopicSuggest({ q: keyword, limit: 6 })
    .then((list) => {
      if (which === 'from') suggestionsFrom.value = list
      else suggestionsTo.value = list
    })
    .catch(() => {})
}

function onEnter(which) {
  const list = which === 'from' ? suggestionsFrom.value : suggestionsTo.value
  if (list.length) {
    pick(which, list[0])
    return
  }
  search(which)
}

function pick(which, topic) {
  if (which === 'from') {
    from.value = topic
    keywordFrom.value = topic.name
    suggestionsFrom.value = []
  } else {
    to.value = topic
    keywordTo.value = topic.name
    suggestionsTo.value = []
  }
}

async function plan() {
  if (!canPlan.value) return
  planning.value = true
  error.value = ''
  try {
    plans.value = await planRoutes([from.value.id, to.value.id])
    selected.value = 0
  } catch (err) {
    error.value = err.message || '规划失败'
    plans.value = []
  } finally {
    planning.value = false
  }
}

onMounted(async () => {
  await load()
  const preset = route.query.to
  if (!preset) return
  try {
    const topic = await getTopic(String(preset))
    to.value = { id: topic.id, name: topic.name }
    keywordTo.value = topic.name
  } catch {
    // 预填失败不影响手动选择
  }
})
</script>

<template>
  <div class="page page-two nav-page">
    <main>
      <div class="card nav-toolbar">
        <div class="pick">
          <label>起点</label>
          <input v-model="keywordFrom" placeholder="搜地点" @input="search('from')" @keydown.enter.prevent="onEnter('from')" />
          <div v-if="suggestionsFrom.length" class="suggest-list">
            <button v-for="item in suggestionsFrom" :key="item.id" type="button" class="suggest-item" @click="pick('from', item)">
              {{ item.name }}
            </button>
          </div>
        </div>
        <div class="pick">
          <label>终点</label>
          <input v-model="keywordTo" placeholder="搜地点" @input="search('to')" @keydown.enter.prevent="onEnter('to')" />
          <div v-if="suggestionsTo.length" class="suggest-list">
            <button v-for="item in suggestionsTo" :key="item.id" type="button" class="suggest-item" @click="pick('to', item)">
              {{ item.name }}
            </button>
          </div>
        </div>
        <button type="button" class="btn primary" :disabled="!canPlan || planning" @click="plan">
          {{ planning ? '规划中…' : '规划路线' }}
        </button>
        <span v-if="planHint" class="muted-text">{{ planHint }}</span>
      </div>

      <div class="card map-card">
        <p v-if="loading" class="muted-text">正在加载底图…</p>
        <p v-else-if="error" class="muted-text">{{ error }}</p>
        <svg v-else class="map" :viewBox="`0 0 ${MAP_W} ${MAP_H}`" role="img" aria-label="校园地图">
          <image href="/map.png" :width="MAP_W" :height="MAP_H" class="map-base" />
          <line
            v-for="(edge, index) in edges"
            :key="`e${index}`"
            :x1="nodeById.get(edge.from)?.x * MAP_W"
            :y1="nodeById.get(edge.from)?.y * MAP_H"
            :x2="nodeById.get(edge.to)?.x * MAP_W"
            :y2="nodeById.get(edge.to)?.y * MAP_H"
            class="map-edge"
          />
          <polyline v-if="highlighted" :points="highlighted" class="map-route" />
          <circle
            v-for="node in nodes"
            :key="node.id"
            :cx="node.x * MAP_W"
            :cy="node.y * MAP_H"
            :r="node.type === 'DESTINATION' ? 4 : 2"
            :class="node.type === 'DESTINATION' ? 'map-node dest' : 'map-node'"
          />
        </svg>
        <p class="muted-text map-tip">示意图：{{ nodes.length }} 个节点 · {{ edges.length }} 条路；比例坐标，不是真实地图。</p>
      </div>
    </main>

    <aside class="side">
      <section class="card">
        <h3>路线方案</h3>
        <p class="muted-text">按综合耗时排序（时间 × 拥挤度）。</p>
        <p v-if="!plans.length" class="muted-text">选两个地点，点「规划路线」。</p>
        <div v-for="(plan, index) in plans" :key="index" class="plan" :class="{ on: index === selected }" @click="selected = index">
          <div class="plan-head">
            <b>方案 {{ index + 1 }}</b>
            <span class="muted-text">{{ plan.totalDistanceMeters }} 米 · 约 {{ Math.round(plan.totalTimeSeconds / 60) }} 分钟</span>
          </div>
          <div class="plan-segments">
            <div v-for="(segment, i) in displaySegments(plan)" :key="i" class="plan-segment">
              <span class="dot" :style="{ background: segment.junction ? '#b9b2a4' : congestionColor(segment.congestion) }"></span>
              <span>{{ segment.label }}</span>
              <span class="muted-text">{{ segment.distanceMeters }}m</span>
            </div>
          </div>
        </div>
      </section>
    </aside>
  </div>
</template>
