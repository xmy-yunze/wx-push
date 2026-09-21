import { reactive } from 'vue'
import * as authApi from '../api/auth'

/**
 * 登录态 —— 一个极轻量的全局状态，刻意不引入 Pinia。
 *
 * 项目需要共享的状态只有「当前用户」这一项，为它装一个状态管理库不划算
 * （Vue 3 的 reactive 对象在模块作用域里天然就是单例，导出去谁都能读）。
 * 等将来状态真的多起来了，再换 Pinia 也只是改这一个文件。
 */
export const authState = reactive({
  /** 当前登录用户；null = 未登录 */
  user: null,

  /**
   * 是否已经向后端确认过登录态。
   *
   * 单独需要这个标志是因为「未登录」和「还没问过后端」在 user 上都是 null，
   * 但路由守卫对这两者的处理完全不同：后者要先发一次探测请求。
   */
  loaded: false
})

/** 正在进行的探测请求，用于合并并发调用（页面刷新时守卫可能被触发多次） */
let pending = null

/** 登录成功后写入当前用户 */
export function setUser(user) {
  authState.user = user
  authState.loaded = true
}

/** 清空登录态（登出、或探测确认未登录） */
export function clearUser() {
  authState.user = null
  authState.loaded = true
}

/**
 * 向后端确认登录态。
 *
 * 未登录时返回 null 而不是抛异常 —— 调用方（路由守卫）只关心「有没有登录」，
 * 不需要处理异常分支。
 *
 * 用 pending 做并发合并：首次进入页面时路由守卫可能连续触发，
 * 不加这层会同时发出多个 /auth/me 请求。
 *
 * @returns {Promise<object|null>} 已登录返回用户对象，否则 null
 */
export function fetchCurrentUser() {
  if (pending) {
    return pending
  }

  const request = authApi.getCurrentUser()
    .then((user) => {
      setUser(user)
      return user
    })
    .catch(() => {
      clearUser()
      return null
    })
    .finally(() => {
      // 只有当前这次请求仍是「最新」的才清空，避免把后来者的 pending 抹掉
      if (pending === request) {
        pending = null
      }
    })

  pending = request
  return pending
}

/**
 * 登出。
 *
 * 即使后端调用失败也要清掉本地状态：用户的意图是「退出」，
 * 不该因为网络抖动就让他卡在已登录界面上。
 */
export async function signOut() {
  try {
    await authApi.logout()
  } catch {
    // 服务端可能已经没有这个会话了，忽略即可
  }
  clearUser()
}
