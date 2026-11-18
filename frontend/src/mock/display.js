// P0 展示数据：mockup 里有、API 契约 P0 还没有对应字段的区块。
// 这些不是接口字段；P1/P2 的 nearby、nav、related、follow 接口上线后整块替换。
export const followedPlaces = [
  { id: 113, name: '第三教学楼' },
  { id: 114, name: '图书馆' },
  { id: 115, name: '学一食堂' },
]

// 「全部」按 mockup 的展示值；分类计数之和是 333，mockup 自身也不相等，接口上线后换成真实总数
export const categoryTotal = 412

export const dailyRoute = {
  steps: [
    { place: '主楼', note: '报到 · 领材料' },
    { place: '学一食堂', note: '办卡' },
    { place: '宿舍', note: '放行李' },
    { place: '图书馆', note: '开网' },
  ],
}

export const placeDisplay = {
  113: {
    sub: '教学楼 · 5 层 · 自习位 420',
    facts: [
      ['开放时间', '07:00 – 23:00'],
      ['楼层', '1 – 5 层'],
      ['骑行', '允许'],
      ['分类', '教学楼'],
    ],
    nearby: [
      { name: '教二', distance: '120m', width: 52 },
      { name: '学一食堂', distance: '310m', width: 78 },
      { name: '图书馆', distance: '480m', width: 64 },
    ],
  },
}

export const postDisplay = {
  1: { actions: ['收藏', '分享'] },
  2: {
    hasImage: true,
    tallImage: true,
    actions: ['按这个顺序走', '收藏'],
    stopLabels: ['起点', '连廊', '终点'],
    routeSummary: '全程 620 米 · 约 8 分钟',
    related: ['雨天从教二到食堂怎么走', '图书馆北门的近路', '晚上十点以后还能去哪自习'],
  },
  3: { actions: ['按这个顺序走', '收藏'] },
  4: { hasImage: true, actions: ['收藏'] },
  5: { actions: ['收藏'] },
  6: { actions: ['收藏', '分享'] },
}
