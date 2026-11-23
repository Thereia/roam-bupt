<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

// 底图坐标系：SVG viewBox 固定 748×1089，节点用 0–1 比例坐标
const MAP_W = 748
const MAP_H = 1089
const MAX_ZOOM = 2.5
// 全图模式：把整张底图缩到框里（可能小于 1×）
const fitZoom = computed(() => {
  if (!size.w || !size.h || !baseScale.value) return 1
  return Math.min(size.w / (MAP_W * baseScale.value), size.h / (MAP_H * baseScale.value))
})
const MIN_ZOOM = computed(() => Math.min(1, fitZoom.value))
// 按下后移动超过这个距离算拖动，不再当成点选
const DRAG_THRESHOLD = 5

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  edges: { type: Array, default: () => [] },
  places: { type: Array, default: () => [] },
  fromNodeId: { type: Number, default: null },
  toNodeId: { type: Number, default: null },
  routeNodeIds: { type: Array, default: () => [] },
})

const emit = defineEmits(['pick'])

const frame = ref(null)
const size = reactive({ w: 0, h: 0 })
const zoom = ref(1)
const offset = reactive({ x: 0, y: 0 })
const dragging = ref(false)
const hoveredId = ref(null)

let initialized = false
let observer = null

const baseScale = computed(() => {
  if (!size.w || !size.h) return 0
  // 铺满：取宽高两个方向里更大的缩放，地图一定比框大（或至少一边撑满）
  return Math.max(size.w / MAP_W, size.h / MAP_H)
})

const contentW = computed(() => MAP_W * baseScale.value * zoom.value)
const contentH = computed(() => MAP_H * baseScale.value * zoom.value)

const canvasStyle = computed(() => ({
  width: `${MAP_W * baseScale.value}px`,
  height: `${MAP_H * baseScale.value}px`,
}))

const moverStyle = computed(() => ({
  transform: `translate(${offset.x}px, ${offset.y}px) scale(${zoom.value})`,
}))

const canZoomIn = computed(() => zoom.value < MAX_ZOOM - 0.001)
const canZoomOut = computed(() => zoom.value > MIN_ZOOM.value + 0.001)
const atFit = computed(() => Math.abs(zoom.value - fitZoom.value) < 0.01)

const nodeById = computed(() => new Map(props.nodes.map((node) => [node.id, node])))

const placeIds = computed(() => new Set(props.places.map((place) => place.nodeId)))

// 路口和楼内房间点只画小圆点，不参与点选
const plainNodes = computed(() => props.nodes.filter((node) => !placeIds.value.has(node.id)))

const routePoints = computed(() =>
  props.routeNodeIds
    .map((id) => nodeById.value.get(id))
    .filter(Boolean)
    .map((node) => `${(node.x * MAP_W).toFixed(1)},${(node.y * MAP_H).toFixed(1)}`)
    .join(' '),
)

// 一个屏幕像素等于多少地图单位。点、名字、描边都按屏幕像素定尺寸，
// 放大时不会跟着涨成大圆点、大字
const unitScale = computed(() => baseScale.value * zoom.value || 1)
const px = (value) => value / unitScale.value

// 底图本身已经印了地名，所以只在「选中」和「鼠标停在这个点上」时补名字，避免叠字
const labels = computed(() =>
  props.places
    .filter((place) => place.nodeId === hoveredId.value)
    .map((place) => ({
      nodeId: place.nodeId,
      text: place.name,
      x: place.x * MAP_W + px(11),
      y: place.y * MAP_H + px(5),
      font: px(13),
    })),
)

function clampOffset() {
  // 内容比框小时居中，比框大时不允许露出底色
  const slackX = size.w - contentW.value
  const slackY = size.h - contentH.value
  offset.x = slackX >= 0 ? slackX / 2 : Math.min(0, Math.max(slackX, offset.x))
  offset.y = slackY >= 0 ? slackY / 2 : Math.min(0, Math.max(slackY, offset.y))
}

function centerOffset() {
  offset.x = (size.w - contentW.value) / 2
  offset.y = (size.h - contentH.value) / 2
  clampOffset()
}

// 缩放围绕框中心：屏幕中心对着的那个地图点保持不动
function applyZoom(next) {
  const target = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM.value, Math.round(next * 1000) / 1000))
  if (!size.w || !size.h || target === zoom.value) return
  const centerMapX = (size.w / 2 - offset.x) / (baseScale.value * zoom.value)
  const centerMapY = (size.h / 2 - offset.y) / (baseScale.value * zoom.value)
  zoom.value = target
  offset.x = size.w / 2 - centerMapX * baseScale.value * target
  offset.y = size.h / 2 - centerMapY * baseScale.value * target
  clampOffset()
}

function onWheel(event) {
  event.preventDefault()
  const rect = frame.value?.getBoundingClientRect()
  if (!rect) return
  const anchorX = event.clientX - rect.left
  const anchorY = event.clientY - rect.top
  const mapX = (anchorX - offset.x) / (baseScale.value * zoom.value)
  const mapY = (anchorY - offset.y) / (baseScale.value * zoom.value)
  const step = event.deltaY < 0 ? 1.15 : 1 / 1.15
  const next = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM.value, zoom.value * step))
  zoom.value = Math.round(next * 1000) / 1000
  offset.x = anchorX - mapX * baseScale.value * zoom.value
  offset.y = anchorY - mapY * baseScale.value * zoom.value
  clampOffset()
}

function zoomIn() {
  applyZoom(zoom.value + 0.25)
}

function zoomOut() {
  applyZoom(Math.max(MIN_ZOOM.value, zoom.value - 0.25))
}

function fitAll() {
  if (!size.w || !size.h) return
  zoom.value = fitZoom.value
  offset.x = (size.w - contentW.value) / 2
  offset.y = (size.h - contentH.value) / 2
  clampOffset()
}

function observeFrame() {
  if (!frame.value || !window.ResizeObserver) return
  observer = new ResizeObserver((entries) => {
    const box = entries[0].contentRect
    const previousBase = baseScale.value
    const centerMapX = previousBase ? (size.w / 2 - offset.x) / (previousBase * zoom.value) : MAP_W / 2
    const centerMapY = previousBase ? (size.h / 2 - offset.y) / (previousBase * zoom.value) : MAP_H / 2
    size.w = box.width
    size.h = box.height
    if (!initialized) {
      initialized = true
      centerOffset()
      return
    }
    offset.x = size.w / 2 - centerMapX * baseScale.value * zoom.value
    offset.y = size.h / 2 - centerMapY * baseScale.value * zoom.value
    clampOffset()
  })
  observer.observe(frame.value)
}

const pointer = { id: null, x: 0, y: 0, ox: 0, oy: 0, moved: false }

function onPointerDown(event) {
  // 缩放按钮自己处理点击，别被拖动逻辑吞掉
  if (event.target.closest?.('.map-zoom')) return
  if (event.pointerType === 'mouse' && event.button !== 0) return
  pointer.id = event.pointerId
  pointer.x = event.clientX
  pointer.y = event.clientY
  pointer.ox = offset.x
  pointer.oy = offset.y
  pointer.moved = false
  try {
    frame.value?.setPointerCapture(event.pointerId)
  } catch {
    // 指针已经抬起，捕获失败不影响后续
  }
}

function onPointerMove(event) {
  if (pointer.id === null || event.pointerId !== pointer.id) return
  const dx = event.clientX - pointer.x
  const dy = event.clientY - pointer.y
  if (!pointer.moved && Math.hypot(dx, dy) > DRAG_THRESHOLD) {
    pointer.moved = true
    dragging.value = true
  }
  if (!pointer.moved) return
  offset.x = pointer.ox + dx
  offset.y = pointer.oy + dy
  clampOffset()
}

function onPointerUp(event) {
  if (pointer.id === null) return
  const moved = pointer.moved
  pointer.id = null
  dragging.value = false
  try {
    if (frame.value?.hasPointerCapture?.(event.pointerId)) frame.value.releasePointerCapture(event.pointerId)
  } catch {
    // 没有捕获就没什么可释放的
  }
  if (moved) return
  const target = document.elementFromPoint(event.clientX, event.clientY)
  const placeEl = target?.closest?.('.map-place')
  if (!placeEl) return
  const place = props.places.find((item) => item.nodeId === Number(placeEl.dataset.nodeId))
  if (!place) return
  emit('pick', place)
}

function onPlaceActivate(place, event) {
  event?.preventDefault?.()
  emit('pick', place)
}

// 起终点不在当前可见区域内时（比如从搜索框选的），把地图挪过去，别让选中点藏在框外
function revealStops() {
  if (!size.w || !size.h) return
  const ids = [props.fromNodeId, props.toNodeId].filter((id) => id != null)
  const targets = ids.map((id) => nodeById.value.get(id)).filter(Boolean)
  if (!targets.length) return
  const scale = baseScale.value * zoom.value
  const margin = 28
  const inside = (node) => {
    const x = offset.x + node.x * MAP_W * scale
    const y = offset.y + node.y * MAP_H * scale
    return x >= margin && x <= size.w - margin && y >= margin && y <= size.h - margin
  }
  if (targets.every(inside)) return
  const anchorX = targets.reduce((sum, node) => sum + node.x * MAP_W, 0) / targets.length
  const anchorY = targets.reduce((sum, node) => sum + node.y * MAP_H, 0) / targets.length
  offset.x = size.w / 2 - anchorX * scale
  offset.y = size.h / 2 - anchorY * scale
  clampOffset()
}

watch([() => props.fromNodeId, () => props.toNodeId], revealStops)

function isFrom(place) {
  return place.nodeId === props.fromNodeId
}

function isTo(place) {
  return place.nodeId === props.toNodeId
}

function dotRadius(place) {
  return px(isFrom(place) || isTo(place) ? 8 : 5)
}

onMounted(observeFrame)

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <div
    ref="frame"
    class="map-frame"
    :class="{ dragging }"
    role="group"
    aria-label="校园地图，可拖动平移、可缩放"
    @wheel.prevent="onWheel"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
  >
    <div class="map-mover" :style="moverStyle">
      <svg class="map-canvas" :viewBox="`0 0 ${MAP_W} ${MAP_H}`" :style="canvasStyle">
        <image href="/map.png" :width="MAP_W" :height="MAP_H" class="map-base" aria-hidden="true" />
        <line
          v-for="(edge, index) in edges"
          :key="`e${index}`"
          :x1="nodeById.get(edge.from)?.x * MAP_W"
          :y1="nodeById.get(edge.from)?.y * MAP_H"
          :x2="nodeById.get(edge.to)?.x * MAP_W"
          :y2="nodeById.get(edge.to)?.y * MAP_H"
          class="map-edge"
        />
        <polyline v-if="routePoints" :points="routePoints" class="map-route" />
        <circle
          v-for="node in plainNodes"
          :key="node.id"
          :cx="node.x * MAP_W"
          :cy="node.y * MAP_H"
          :r="px(node.type === 'DESTINATION' ? 2 : 1.5)"
          class="map-node"
          :class="{ dest: node.type === 'DESTINATION' }"
        />
        <g
          v-for="place in places"
          :key="place.nodeId"
          class="map-place"
          :class="{ 'is-from': isFrom(place), 'is-to': isTo(place) }"
          :data-node-id="place.nodeId"
          :data-name="place.name"
          role="button"
          tabindex="0"
          :aria-label="`${place.name}${isFrom(place) ? '（起点）' : isTo(place) ? '（终点）' : ''}`"
          @pointerenter="hoveredId = place.nodeId"
          @pointerleave="hoveredId = null"
          @keydown.enter.prevent="onPlaceActivate(place, $event)"
          @keydown.space.prevent="onPlaceActivate(place, $event)"
        >
          <title>{{ place.name }}</title>
          <circle class="map-place-hit" :cx="place.x * MAP_W" :cy="place.y * MAP_H" :r="px(15)" />
          <circle
            class="map-place-dot"
            :cx="place.x * MAP_W"
            :cy="place.y * MAP_H"
            :r="dotRadius(place)"
            :stroke-width="px(1.5)"
          />
          <text
            v-if="isFrom(place) || isTo(place)"
            class="map-place-index"
            :x="place.x * MAP_W"
            :y="place.y * MAP_H + px(3.5)"
            :font-size="px(10)"
          >{{ isFrom(place) ? 'A' : 'B' }}</text>
        </g>
        <g class="map-labels" aria-hidden="true">
          <text
            v-for="label in labels"
            :key="label.nodeId"
            class="map-place-label"
            :x="label.x"
            :y="label.y"
            :font-size="label.font"
            :stroke-width="px(2.5)"
          >{{ label.text }}</text>
        </g>
      </svg>
    </div>

    <div class="map-zoom">
      <button type="button" class="map-zoom-btn" data-zoom="in" :disabled="!canZoomIn" title="放大" @click="zoomIn">+</button>
      <button type="button" class="map-zoom-btn" data-zoom="out" :disabled="!canZoomOut" title="缩小" @click="zoomOut">−</button>
      <button type="button" class="map-zoom-btn map-fit-btn" data-zoom="fit" :disabled="atFit" title="看全图" @click="fitAll">全图</button>
    </div>
  </div>
</template>

<style scoped>
.map-frame {
  position: relative;
  height: 420px;
  overflow: hidden;
  background: #f3f1ea;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.map-frame.dragging {
  cursor: grabbing;
}

.map-mover {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: 0 0;
  will-change: transform;
}

.map-canvas {
  display: block;
}

.map-place-hit {
  fill: transparent;
  cursor: pointer;
}

/* 点的 outline 会画成一个大黑方框，焦点改用圆点自身变绿表示 */
.map-place:focus {
  outline: none;
}

.map-place:focus-visible .map-place-dot {
  fill: var(--accent);
}

.map-place-dot {
  fill: #7d7666;
  stroke: #ffffff;
  pointer-events: none;
}

.map-place:hover .map-place-dot {
  fill: #3f3a32;
}

.map-place.is-from .map-place-dot {
  fill: var(--accent);
}

.map-place.is-to .map-place-dot {
  fill: var(--danger);
}

.map-place-index {
  fill: #ffffff;
  font-weight: 600;
  text-anchor: middle;
  pointer-events: none;
}

.map-labels {
  pointer-events: none;
}

.map-place-label {
  fill: #4c473f;
  paint-order: stroke;
  stroke: #f7f6f2;
  stroke-linejoin: round;
}

.map-zoom {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.map-zoom-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  font-size: 16px;
  line-height: 1;
  color: var(--ink);
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.map-zoom-btn:hover:not(:disabled) {
  border-color: var(--accent);
  color: var(--accent);
}

.map-zoom-btn:disabled {
  color: var(--muted);
  opacity: 0.5;
  cursor: default;
}
</style>
