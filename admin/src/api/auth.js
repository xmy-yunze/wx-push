import request from './request'

/**
 * 鉴权相关接口。
 * 字段与错误码约定见 docs/admin-接口契约.md 第四节。
 */

/**
 * 登录。
 *
 * @param {string} username 账号
 * @param {string} password 明文密码（后端只保存 PBKDF2 哈希，库里没有明文）
 * @returns {Promise<{id:number, username:string, displayName:string}>}
 */
export function login(username, password) {
  return request.post('/auth/login', { username, password })
}

/**
 * 登出 —— 销毁服务端会话。
 *
 * 未登录时调用也会返回成功（后端把它做成幂等的），
 * 所以前端不需要先判断登录态再决定要不要调。
 */
export function logout() {
  return request.post('/auth/logout')
}

/**
 * 取当前登录用户。
 *
 * 未登录时后端返回 401。这里标记 `silent: true`，
 * 让 axios 拦截器跳过全局错误弹窗 —— 探测登录态属于「预期内可能失败」的请求，
 * 不该在页面刚打开时就弹一个「登录已过期」的红条吓用户。
 *
 * @returns {Promise<{id:number, username:string, displayName:string}>}
 */
export function getCurrentUser() {
  return request.get('/auth/me', { silent: true })
}
