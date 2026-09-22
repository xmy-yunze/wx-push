import request from './request'

/**
 * 自定义菜单相关接口。
 *
 * ⚠️ 与消息接口的区别：菜单的「真相源在微信服务器」，本地不落库。
 * 所以这里查到的永远是线上真实状态（包括在公众平台手工改过的部分）。
 */

/**
 * 查询当前在微信服务器上生效的菜单。
 *
 * @returns {Promise<{menu:{button:Array}}>} 微信的原始结构
 */
export function fetchMenu() {
  return request.get('/menu')
}

/**
 * 发布菜单（覆盖线上现有菜单）。
 *
 * ⚠️ 微信侧失败时会返回 502，message 里是**可直接展示的中文提示**
 * （例如「调用来源 IP 不在公众号后台的白名单内（微信返回 40164：invalid ip）」）。
 * 前端把 message 弹出来即可，不需要自己翻译错误码。
 *
 * @param {object} menu 微信格式的菜单 JSON：{ button: [...] }
 * @param {object} [options]
 * @param {boolean} [options.dryRun=false] 干跑：只校验并回显将要发送的报文，不真的发送
 * @returns {Promise<{published:boolean, payloadJson:string}>}
 */
export function publishMenu(menu, { dryRun = false } = {}) {
  return request.post('/menu', menu, { params: dryRun ? { dryRun: true } : {} })
}

/** 清空线上菜单（公众号恢复成默认状态） */
export function clearMenu() {
  return request.delete('/menu')
}
