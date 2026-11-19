import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn } from '../store/user'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
  {
    path: '/topics',
    name: 'topics',
    component: () => import('../views/PlaceholderView.vue'),
    meta: { title: '地点' },
  },
  {
    path: '/topics/:id',
    name: 'topic',
    component: () => import('../views/TopicView.vue'),
    meta: { title: '地点' },
  },
  {
    path: '/posts/:id',
    name: 'post',
    component: () => import('../views/PostView.vue'),
    meta: { title: '帖子' },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/publish',
    name: 'publish',
    component: () => import('../views/PlaceholderView.vue'),
    meta: { title: '发布', requiresAuth: true },
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('../views/SearchView.vue'),
    meta: { title: '搜索' },
  },
  {
    path: '/nav',
    name: 'nav',
    component: () => import('../views/PlaceholderView.vue'),
    meta: { title: '路线' },
  },
  {
    path: '/notifications',
    name: 'notifications',
    component: () => import('../views/NotificationsView.vue'),
    meta: { title: '通知', requiresAuth: true },
  },
  {
    path: '/users/:id',
    name: 'user',
    component: () => import('../views/PlaceholderView.vue'),
    meta: { title: '个人主页' },
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('../views/PlaceholderView.vue'),
    meta: { title: '管理后台' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../views/NotFoundView.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !isLoggedIn()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · roam.bupt` : 'roam.bupt · 校园地点社区'
})

export default router
