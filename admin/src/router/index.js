import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layout/AppLayout.vue'
import { authState, fetchCurrentUser } from '../store/auth'

/**
 * 路由表。
 *
 * 结构上分两类：
 *   - 带布局的页面（看板 / 消息）挂在 AppLayout 下，共享侧边栏和顶栏；
 *   - 登录页独立在外 —— 它不该出现侧边栏。
 *
 * 页面组件全部用动态 import（懒加载），首屏只加载当前页面的代码。
 */
const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/DashboardView.vue'),
        meta: { title: '数据看板' }
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('../views/MessageListView.vue'),
        meta: { title: '消息记录' }
      }
    ]
  },
  // 兜底：访问不存在的路径一律回看板，避免出现空白页
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局前置守卫 —— 登录门禁。
 *
 * 判断依据是后端会话是否有效，而不是本地存的一个标记位：
 * 本地标记可以被手工改掉，而且服务端会话过期后它也不会自动失效，
 * 那样会出现「看起来已登录、点什么都 401」的尴尬状态。
 *
 * 首次进入或刷新页面时，本地还没有登录态，此时发一次 /auth/me 问后端。
 * 这也是为什么这个请求被标记为 silent —— 未登录是它的正常返回之一。
 */
router.beforeEach(async (to) => {
  // 登录页：已登录就别再进来了，直接回看板
  if (to.path === '/login') {
    return authState.loaded && authState.user ? { path: '/dashboard' } : true
  }

  if (authState.loaded) {
    // 已经问过后端：有用户就放行，没有就直接去登录页，不再重复探测
    return authState.user
      ? true
      : { path: '/login', query: { redirect: to.fullPath } }
  }

  // 还没问过后端（首次进入 / 刷新页面）→ 探测一次
  const user = await fetchCurrentUser()
  return user
    ? true
    : { path: '/login', query: { redirect: to.fullPath } }
})

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} · wx-push 管理后台` : 'wx-push 管理后台'
})

export default router
