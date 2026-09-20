import request from './request'

/**
 * 消息记录相关接口。
 * 字段与错误码约定见 docs/admin-接口契约.md 第二节。
 */

/**
 * 分页查询消息列表。
 *
 * @param {object} params 查询条件，全部可选
 * @param {number} [params.page=1]      页码，从 1 开始
 * @param {number} [params.size=20]     每页条数，后端上限 100
 * @param {string} [params.msgType]     消息类型：text / event ...
 * @param {string} [params.event]       事件类型：subscribe / unsubscribe ...
 * @param {string} [params.startDate]   起始日期 yyyy-MM-dd（含当天）
 * @param {string} [params.endDate]     结束日期 yyyy-MM-dd（含当天）
 * @param {string} [params.keyword]     关键词，匹配 openid 或消息内容
 * @returns {Promise<{total:number, page:number, size:number, list:Array}>}
 */
export function fetchMessages(params) {
  return request.get('/messages', { params })
}

/**
 * 查询单条消息详情。
 *
 * ⚠️ 注意返回结构与列表不同：这里是**裸对象**，不是分页结构。
 *
 * @param {number} id 消息主键
 */
export function fetchMessageDetail(id) {
  return request.get(`/messages/${id}`)
}
