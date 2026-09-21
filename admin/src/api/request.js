import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/**
 * axios 实例 —— 所有请求的统一出口。
 *
 * 这里做了四件对全局有影响的事：
 *
 * 1. **baseURL 设为 `/api`**：配合 vite.config.js 里的 proxy，
 *    开发期请求会打到 5173 再由 Vite 转发给后端 8080，浏览器看到的是同源请求。
 *
 * 2. **withCredentials = true**：鉴权方案是 Session + Cookie，
 *    不开这个开关，浏览器不会带上 JSESSIONID，后端永远认为你没登录。
 *
 * 3. **响应拦截器拆壳**：后端统一返回 { code, message, data }，
 *    拦截器直接把 data 返回出去 —— 于是业务代码里 `await fetchMessages()`
 *    拿到的就是纯粹的列表数据，不用层层 `.data.data`，
 *    也不会出现「有的地方忘了判断 code」这种漏网之鱼。
 *
 * 4. **401 统一跳登录页**：会话过期时自动把用户送回登录页，
 *    并在 query 里记住他原本想去的地址，登录后可以跳回去。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true
})

request.interceptors.response.use(
  // HTTP 2xx：还要再看业务码
  (response) => {
    const body = response.data

    if (body && body.code === 0) {
      return body.data
    }

    const msg = body?.message || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },

  // HTTP 非 2xx：网络层或服务端错误
  (error) => {
    const status = error.response?.status
    const msg = error.response?.data?.message

    // 单个请求可以传 { silent: true } 来跳过全局错误弹窗。
    // 用于「探测型」请求 —— 比如开机时问后端「我登录了吗」，
    // 未登录是预期结果，不该弹红条吓用户。
    const silent = error.config?.silent === true

    if (status === 401) {
      if (!silent) {
        ElMessage.error(msg || '登录已过期，请重新登录')
      }

      // ⚠️ 只有「当前不在登录页」时才跳转。
      // 原因：登录失败的响应也是 401。若不加这个判断，用户在登录页输错密码
      // 会被重定向到 /login?redirect=/login，登录成功后又跳回登录页 —— 逻辑死循环。
      const current = router.currentRoute.value
      if (current.path !== '/login') {
        router.push({ path: '/login', query: { redirect: current.fullPath } })
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查后端服务是否已启动')
    } else if (!silent) {
      ElMessage.error(msg || `请求异常：${error.message}`)
    }

    return Promise.reject(error)
  }
)

export default request
