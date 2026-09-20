import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layout/AppLayout.vue'

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

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} · wx-push 管理后台` : 'wx-push 管理后台'
})

export default router
