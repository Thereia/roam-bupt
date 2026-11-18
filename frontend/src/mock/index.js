import { posts, postContents, commentsByPost } from './posts'
import { topics } from './topics'

const MAX_PAGE_SIZE = 50
const MOCK_DELAY = 200

export function withMock(handler) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      try {
        resolve(handler())
      } catch (error) {
        reject(error)
      }
    }, MOCK_DELAY)
  })
}

function pageOf(list, page = 1, pageSize = 10) {
  const size = Math.min(Number(pageSize) || 10, MAX_PAGE_SIZE)
  const current = Math.max(Number(page) || 1, 1)
  const start = (current - 1) * size
  return { total: list.length, page: current, pageSize: size, list: list.slice(start, start + size) }
}

function notFound(message) {
  const error = new Error(message)
  error.code = 404
  return error
}

const byNewest = (a, b) => b.createdTime.localeCompare(a.createdTime)
const byHot = (a, b) => b.counts.like - a.counts.like

export function listPosts({ page = 1, pageSize = 10, feed = 'recommend' } = {}) {
  // P0 没有关注关系数据，following 返回空列表，由页面提示登录/空态
  if (feed === 'following') return pageOf([], page, pageSize)
  const online = posts.filter((post) => post.status === 'ONLINE')
  // recommend 保持 fixture 的编排顺序，latest 才按时间倒序
  const list = [...online]
  if (feed === 'latest') list.sort(byNewest)
  return pageOf(list, page, pageSize)
}

export function getPostDetail(id) {
  const post = posts.find((item) => item.id === Number(id))
  if (!post || post.status !== 'ONLINE') throw notFound('帖子不存在或已下线')
  return { ...post, content: postContents[post.id] || '' }
}

export function listComments(postId, { page = 1, pageSize = 10 } = {}) {
  return pageOf(commentsByPost[Number(postId)] || [], page, pageSize)
}

export function listTopics({ type = 'PLACE', category, q, sort, page = 1, pageSize = 50 } = {}) {
  const sorters = {
    heat: (a, b) => b.heat - a.heat,
    rating: (a, b) => b.rating.displayAvg - a.rating.displayAvg,
    postCount: (a, b) => b.postCount - a.postCount,
  }
  let list = topics.filter((topic) => topic.type === type)
  if (category) list = list.filter((topic) => topic.category === category)
  const keyword = (q || '').trim()
  if (keyword) list = list.filter((topic) => topic.name.includes(keyword))
  // 不传 sort 时保留 fixture 顺序，和 GET /topics 的默认顺序一致
  if (sort && sorters[sort]) list = [...list].sort(sorters[sort])
  return pageOf(list, page, pageSize)
}

export function getTopicDetail(id) {
  const topic = topics.find((item) => item.id === Number(id))
  if (!topic) throw notFound('这个地点不存在')
  return topic
}

export function listTopicPosts(topicId, { sort = 'latest', page = 1, pageSize = 10 } = {}) {
  const id = Number(topicId)
  const list = posts.filter((post) => post.status === 'ONLINE' && post.topics.some((topic) => topic.id === id))
  if (sort === 'hot') list.sort(byHot)
  else list.sort(byNewest)
  return pageOf(list, page, pageSize)
}
