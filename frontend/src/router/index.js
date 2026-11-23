import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn, userStore } from '../store/user'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
  {
    path: '/topics',
    name: 'topics',
    component: () => import('../views/TopicsView.vue'),
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
    component: () => import('../views/PublishView.vue'),
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
    component: () => import('../views/NavView.vue'),
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
    component: () => import('../views/UserView.vue'),
    meta: { title: '个人主页' },
  },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    meta: { title: '管理后台', requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', redirect: '/admin/posts' },
      { path: 'posts', name: 'adminPosts', component: () => import('../views/admin/AdminPostsView.vue') },
      { path: 'users', name: 'adminUsers', component: () => import('../views/admin/AdminUsersView.vue') },
      { path: 'words', name: 'adminWords', component: () => import('../views/admin/AdminWordsView.vue') },
    ],
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
  if (to.matched.some((record) => record.meta.requiresAdmin)
      && userStore.user?.role !== 'ADMIN') {
    return { path: '/' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · roam.bupt` : 'roam.bupt · 校园地点社区'
})

export default router
