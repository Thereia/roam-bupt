<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getMap, planRoutes } from '../api/space'
import { getTopic, getTopicStats, getTopicSuggest, getTopics } from '../api/topic'
import NavMap from '../components/NavMap.vue'

const route = useRoute()

const loading = ref(true)
const mapError = ref('')
const planError = ref('')
const nodes = ref([])
const edges = ref([])
const plans = ref([])
const selected = ref(0)
const planning = ref(false)
const places = ref([])

const MODES = [
  { key: 'MIXED', label: '综合（时间 × 拥挤度）' },
  { key: 'TIME', label: '最快' },
  { key: 'DISTANCE', label: '最短' },
]
const mode = ref('MIXED')

// 多目的地：至少 2 个点，可插入途经点（最多 9 个）
const stops = ref([emptyStop(), emptyStop()])
const suggestionIndex = ref(-1)
const suggestions = ref([])

function emptyStop() {
  return { place: null, keyword: '' }
}

const filled = computed(() => stops.value.map((stop) => stop.place).filter(Boolean))
// 只剩一个点时不要把同一个点同时当起点和终点
const fromNodeId = computed(() => (filled.value.length >= 1 ? filled.value[0].nodeId : null))
const toNodeId = computed(() =>
  filled.value.length >= 2 ? filled.value[filled.value.length - 1].nodeId : null,
)
const planNodeIds = computed(() => plans.value[selected.value]?.nodeIds ?? [])

const canPlan = computed(() => {
  if (filled.value.length < 2) return false
  const ids = filled.value.map((place) => place.id)
  return new Set(ids).size === ids.length
})

const planHint = computed(() => {
  if (filled.value.length < 2) return '选起点和终点：点地图上的地点，或搜地点'
  const ids = filled.value.map((place) => place.id)
  if (new Set(ids).size !== ids.length) return '地点不能重复'
  return ''
})

const nodeById = computed(() => new Map(nodes.value.map((node) => [node.id, node])))
const topicNode = computed(() => new Map(places.value.map((place) => [place.topicId, place.nodeId])))

function stopLabel(index) {
  if (index === 0) return '起点'
  if (index === stops.value.length - 1) return '终点'
  return `途经 ${index}`
}

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
  mapError.value = ''
  try {
    const map = await getMap()
    nodes.value = map.nodes
    edges.value = map.edges
  } catch (err) {
    mapError.value = err.message || '底图加载失败'
  } finally {
    loading.value = false
  }
}

// 一次取回全部地点主题，建 nodeId ⇄ topicId 的对照；失败只影响点选，不影响搜地点。
// 按分类逐个取（每类都小于一页）：后端列表没有稳定排序，靠 page 翻页会重复、漏项。
async function loadPlaces() {
  let rows = []
  try {
    const stats = await getTopicStats()
    const categories = (stats ?? []).filter((item) => item.count > 0).map((item) => item.category)
    const groups = await Promise.all(
      categories.map(async (category) => {
        const collected = []
        for (let page = 1; page <= 5; page += 1) {
          const result = await getTopics({ type: 'PLACE', category, pageSize: 50, page })
          const list = result?.list ?? []
          collected.push(...list)
          if (!list.length || collected.length >= (result?.total ?? collected.length)) break
        }
        return collected
      }),
    )
    rows = groups.flat()
  } catch {
    return
  }
  const byTopic = new Map()
  for (const topic of rows) {
    if (topic?.id != null) byTopic.set(topic.id, topic)
  }
  places.value = [...byTopic.values()]
    .filter((topic) => topic.nodeId != null && nodeById.value.has(topic.nodeId))
    .map((topic) => {
      const node = nodeById.value.get(topic.nodeId)
      return { nodeId: topic.nodeId, topicId: topic.id, name: topic.name, x: node.x, y: node.y }
    })
}

function resetPlans() {
  plans.value = []
  selected.value = 0
  planError.value = ''
}

function fillStop(index, topic, keyword = '') {
  const place = topic
    ? { id: topic.id ?? topic.topicId, name: topic.name, nodeId: topic.nodeId ?? topicNode.value.get(topic.id ?? topic.topicId) ?? null }
    : null
  stops.value[index] = { place, keyword: place ? place.name : keyword }
  resetPlans()
}

function addStop() {
  if (stops.value.length >= 9) return
  stops.value.splice(stops.value.length - 1, 0, emptyStop())
}

function removeStop(index) {
  if (stops.value.length <= 2) return
  stops.value.splice(index, 1)
  resetPlans()
}

// 点地图：已选的取消；否则填第一个空位，没空位就作为新的终点插入
function pickOnMap(place) {
  const existing = stops.value.findIndex((stop) => stop.place?.nodeId != null && stop.place.nodeId === place.nodeId)
  if (existing >= 0) {
    stops.value[existing] = emptyStop()
    resetPlans()
    return
  }
  const empty = stops.value.findIndex((stop) => !stop.place)
  if (empty >= 0) {
    fillStop(empty, { id: place.topicId, name: place.name, nodeId: place.nodeId })
    return
  }
  if (stops.value.length < 9) {
    stops.value.splice(stops.value.length - 1, 0, {
      place: { id: place.topicId, name: place.name, nodeId: place.nodeId },
      keyword: place.name,
    })
    resetPlans()
  }
}

function search(index) {
  suggestionIndex.value = index
  const keyword = stops.value[index].keyword.trim()
  if (!keyword) {
    suggestions.value = []
    return
  }
  getTopicSuggest({ q: keyword, limit: 6 })
    .then((list) => {
      if (suggestionIndex.value === index) suggestions.value = list
    })
    .catch(() => {})
}

function onEnter(index) {
  if (suggestions.value.length && suggestionIndex.value === index) {
    pick(index, suggestions.value[0])
    return
  }
  search(index)
}

function pick(index, topic) {
  fillStop(index, { id: topic.id, name: topic.name, nodeId: topicNode.value.get(topic.id) ?? null })
  suggestions.value = []
  suggestionIndex.value = -1
}

async function plan() {
  if (!canPlan.value) return
  planning.value = true
  planError.value = ''
  try {
    plans.value = await planRoutes(filled.value.map((place) => place.id), mode.value)
    selected.value = 0
  } catch (err) {
    planError.value = err.message || '规划失败'
    plans.value = []
  } finally {
    planning.value = false
  }
}

// 从帖子跳过来：?stops=1,2,3&mode=DISTANCE&auto=1
async function applyPreset() {
  const raw = typeof route.query.stops === 'string' ? route.query.stops : ''
  if (!raw) return
  const ids = raw.split(',').map((item) => Number(item)).filter((id) => Number.isFinite(id))
  if (ids.length < 2) return
  try {
    const topics = await Promise.all(ids.map((id) => getTopic(id)))
    stops.value = topics.map((topic) => ({
      place: { id: topic.id, name: topic.name, nodeId: topic.nodeId ?? topicNode.value.get(topic.id) ?? null },
      keyword: topic.name,
    }))
    if (typeof route.query.mode === 'string' && MODES.some((item) => item.key === route.query.mode)) {
      mode.value = route.query.mode
    }
    if (route.query.auto === '1') await plan()
  } catch (err) {
    planError.value = err.message || '按帖子里的地点规划失败'
  }
}

onMounted(async () => {
  await load()
  await loadPlaces()
  await applyPreset()
  if (route.query.stops) return
  const preset = route.query.to
  if (!preset) return
  try {
    const topic = await getTopic(String(preset))
    fillStop(1, { id: topic.id, name: topic.name, nodeId: topic.nodeId ?? null })
  } catch {
    // 预填失败不影响手动选择
  }
})
</script>

<template>
  <div class="page page-two nav-page">
    <main>
      <div class="card nav-toolbar">
        <div v-for="(stop, index) in stops" :key="index" class="pick">
          <label>{{ stopLabel(index) }}</label>
          <input
            v-model="stop.keyword"
            :data-pick="index === 0 ? 'from' : index === stops.length - 1 ? 'to' : `way-${index}`"
            placeholder="点地图选，或搜地点"
            @input="search(index)"
            @keydown.enter.prevent="onEnter(index)"
          />
          <button
            v-if="stops.length > 2 && index !== 0 && index !== stops.length - 1"
            type="button"
            class="stop-remove"
            title="删除这个途经点"
            @click="removeStop(index)"
          >×</button>
          <div v-if="suggestionIndex === index && suggestions.length" class="suggest-list">
            <button v-for="item in suggestions" :key="item.id" type="button" class="suggest-item" @click="pick(index, item)">
              {{ item.name }}
            </button>
          </div>
        </div>
        <div class="nav-actions">
          <button type="button" class="btn" :disabled="stops.length >= 9" @click="addStop">+ 途经点</button>
          <select v-model="mode" class="mode-select" aria-label="路线策略">
            <option v-for="item in MODES" :key="item.key" :value="item.key">{{ item.label }}</option>
          </select>
          <button type="button" class="btn primary" :disabled="!canPlan || planning" @click="plan">
            {{ planning ? '规划中…' : '规划路线' }}
          </button>
        </div>
        <span v-if="planHint" class="muted-text">{{ planHint }}</span>
      </div>
      <p v-if="planError" class="form-error map-plan-error">{{ planError }}</p>

      <div class="card map-card">
        <p v-if="loading" class="muted-text">正在加载底图…</p>
        <p v-else-if="mapError" class="muted-text">{{ mapError }}</p>
        <template v-else>
          <NavMap
            :nodes="nodes"
            :edges="edges"
            :places="places"
            :from-node-id="fromNodeId"
            :to-node-id="toNodeId"
            :route-node-ids="planNodeIds"
            @pick="pickOnMap"
          />
          <p class="muted-text map-tip">
            示意图：{{ nodes.length }} 个节点 · {{ edges.length }} 条路，{{ places.length }} 个地点可点选；拖动平移，滚轮或右下角 +/− 缩放。
            <template v-if="!places.length">地点列表没加载出来，先用上面的搜索框选起终点。</template>
          </p>
        </template>
      </div>
    </main>

    <aside class="side">
      <section class="card">
        <h3>路线方案</h3>
        <p class="muted-text">{{ MODES.find((item) => item.key === mode)?.label }}为权重，选中方案可在地图上看线路。</p>
        <p v-if="!plans.length" class="muted-text">选好地点，点「规划路线」。</p>

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

<style scoped>
.map-plan-error {
  margin-top: 8px;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.mode-select {
  padding: 6px 8px;
  font: inherit;
  font-size: var(--fs-sm);
  color: var(--ink);
  background: #fbfaf7;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.pick {
  position: relative;
}

.stop-remove {
  position: absolute;
  top: 0;
  right: 0;
  padding: 0 4px;
  color: var(--muted);
  background: transparent;
  border: 0;
  cursor: pointer;
}
</style>
