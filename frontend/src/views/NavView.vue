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

// 能点选的地点：路网节点 + 对应的地点主题（后端寻路只认 topicId）
const places = ref([])

const from = ref(null)
const to = ref(null)
const keywordFrom = ref('')
const keywordTo = ref('')

const suggestionsFrom = ref([])
const suggestionsTo = ref([])

const nodeById = computed(() => new Map(nodes.value.map((node) => [node.id, node])))
const topicNode = computed(() => new Map(places.value.map((place) => [place.topicId, place.nodeId])))

const fromNodeId = computed(() => from.value?.nodeId ?? null)
const toNodeId = computed(() => to.value?.nodeId ?? null)

const planNodeIds = computed(() => plans.value[selected.value]?.nodeIds ?? [])

const canPlan = computed(() => from.value && to.value && from.value.id !== to.value.id)

const planHint = computed(() => {
  if (!from.value) return '先选起点：点地图上的地点，或搜地点'
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

function setStop(which, place) {
  const stop = place ? { id: place.topicId, name: place.name, nodeId: place.nodeId ?? null } : null
  if (which === 'from') {
    from.value = stop
    keywordFrom.value = stop?.name ?? ''
    suggestionsFrom.value = []
  } else {
    to.value = stop
    keywordTo.value = stop?.name ?? ''
    suggestionsTo.value = []
  }
}

// 点地图：先起点、再终点；点已选中的那个就是取消；两个都选好后重新从起点开始
function pickOnMap(place) {
  if (from.value?.nodeId != null && from.value.nodeId === place.nodeId) {
    setStop('from', null)
  } else if (to.value?.nodeId != null && to.value.nodeId === place.nodeId) {
    setStop('to', null)
  } else if (!from.value) {
    setStop('from', place)
  } else if (!to.value) {
    setStop('to', place)
  } else {
    setStop('to', null)
    setStop('from', place)
  }
  resetPlans()
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
  setStop(which, {
    topicId: topic.id,
    name: topic.name,
    nodeId: topicNode.value.get(topic.id) ?? null,
  })
  resetPlans()
}

async function plan() {
  if (!canPlan.value) return
  planning.value = true
  planError.value = ''
  try {
    plans.value = await planRoutes([from.value.id, to.value.id])
    selected.value = 0
  } catch (err) {
    planError.value = err.message || '规划失败'
    plans.value = []
  } finally {
    planning.value = false
  }
}

onMounted(async () => {
  await load()
  await loadPlaces()
  const preset = route.query.to
  if (!preset) return
  try {
    const topic = await getTopic(String(preset))
    if (topic?.type === 'PLACE') {
      setStop('to', { topicId: topic.id, name: topic.name, nodeId: topic.nodeId ?? null })
    } else {
      setStop('to', { topicId: topic.id, name: topic.name, nodeId: null })
    }
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
          <input
            v-model="keywordFrom"
            data-pick="from"
            placeholder="点地图选，或搜地点"
            @input="search('from')"
            @keydown.enter.prevent="onEnter('from')"
          />
          <div v-if="suggestionsFrom.length" class="suggest-list">
            <button v-for="item in suggestionsFrom" :key="item.id" type="button" class="suggest-item" @click="pick('from', item)">
              {{ item.name }}
            </button>
          </div>
        </div>
        <div class="pick">
          <label>终点</label>
          <input
            v-model="keywordTo"
            data-pick="to"
            placeholder="点地图选，或搜地点"
            @input="search('to')"
            @keydown.enter.prevent="onEnter('to')"
          />
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
            示意图：{{ nodes.length }} 个节点 · {{ edges.length }} 条路，{{ places.length }} 个地点可点选；拖动平移，右下角 +/− 缩放。
            <template v-if="!places.length">地点列表没加载出来，先用上面的搜索框选起终点。</template>
          </p>
        </template>
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

<style scoped>
.map-plan-error {
  margin-top: 8px;
}
</style>
