import request from './request'

/**
 * 数据看板统计接口。
 * 字段与错误码约定见 docs/admin-接口契约.md 第三节。
 */

/**
 * 概览指标（看板顶部 6 个卡片）。
 *
 * @returns {Promise<{totalMessages:number, todayMessages:number,
 *   totalSubscribe:number, totalUnsubscribe:number,
 *   netGrowth:number, activeUsers:number}>}
 */
export function fetchOverview() {
  return request.get('/stats/overview')
}

/**
 * 按天趋势（折线图）。
 *
 * ✅ 后端已把「没有数据的日期」补成 0，返回的日期是连续的，
 * 前端不需要自己补日期，直接把结果映射成 xAxis / series 即可。
 *
 * @param {number} [days=7] 最近多少天（含今天），后端上限 90
 * @returns {Promise<Array<{date:string, total:number, subscribe:number, unsubscribe:number}>>}
 */
export function fetchTrend(days = 7) {
  return request.get('/stats/trend', { params: { days } })
}

/**
 * 消息类型分布（饼图）。
 *
 * ✅ 返回的字段名就是 ECharts `series.data` 的标准字段名（name / value），
 * 可以直接赋值，不需要再转换。
 *
 * @returns {Promise<Array<{name:string, value:number}>>}
 */
export function fetchTypeDistribution() {
  return request.get('/stats/type-distribution')
}
