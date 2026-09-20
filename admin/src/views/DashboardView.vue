<script setup>
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, nextTick } from 'vue'
import * as echarts from 'echarts'
import { fetchOverview, fetchTrend, fetchTypeDistribution } from '../api/stats'

/**
 * 数据看板。
 *
 * 三个接口并发请求（Promise.all），任何一个挂了都不影响另外两个的展示
 * —— 这一点由 axios 拦截器统一弹提示，页面自身不做错误处理。
 */

const loading = ref(false)

const overview = ref({
  totalMessages: 0,
  todayMessages: 0,
  totalSubscribe: 0,
  totalUnsubscribe: 0,
  netGrowth: 0,
  activeUsers: 0
})

// 概览卡片。用 computed 派生出 6 张卡，模板里循环渲染，避免写 6 遍重复结构。
const cards = computed(() => {
  const o = overview.value
  return [
    { key: 'total', label: '消息总数', value: o.totalMessages, accent: '#409eff' },
    { key: 'today', label: '今日消息', value: o.todayMessages, accent: '#1d9e75' },
    { key: 'sub', label: '累计关注', value: o.totalSubscribe, accent: '#67c23a' },
    { key: 'unsub', label: '累计取关', value: o.totalUnsubscribe, accent: '#e6a23c' },
    {
      key: 'net',
      label: '净增关注',
      value: o.netGrowth,
      // 净增为负时标红 —— 这是个值得运营一眼注意到的信号
      accent: o.netGrowth < 0 ? '#f56c6c' : '#67c23a'
    },
    { key: 'users', label: '活跃用户', value: o.activeUsers, accent: '#a06cd5' }
  ]
})

const trendEl = ref(null)
const pieEl = ref(null)
const trendChart = shallowRef(null)
const pieChart = shallowRef(null)

// 图表实例用 shallowRef：ECharts 实例内部结构庞大，
// 用 ref 会被 Vue 深度代理，既拖性能又可能干扰 ECharts 内部逻辑。
function ensureTrendChart() {
  if (!trendChart.value && trendEl.value) {
    trendChart.value = echarts.init(trendEl.value)
  }
  return trendChart.value
}

function ensurePieChart() {
  if (!pieChart.value && pieEl.value) {
    pieChart.value = echarts.init(pieEl.value)
  }
  return pieChart.value
}

function renderTrend(list) {
  const chart = ensureTrendChart()
  if (!chart) return

  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['消息总数', '关注', '取关'], bottom: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 12 } },
    grid: { left: 44, right: 20, top: 24, bottom: 44 },
    xAxis: {
      type: 'category',
      // 后端已把缺失日期补成 0，这里直接取，不需要前端补日期
      data: list.map((x) => x.date),
      boundaryGap: false,
      axisLabel: { fontSize: 11, color: '#909399' }
    },
    yAxis: {
      type: 'value',
      // minInterval=1：消息条数是整数，避免 Y 轴出现 0.5 这种刻度
      minInterval: 1,
      axisLabel: { fontSize: 11, color: '#909399' },
      splitLine: { lineStyle: { color: '#f0f2f5' } }
    },
    series: [
      { name: '消息总数', type: 'line', smooth: true, symbolSize: 6, data: list.map((x) => x.total), itemStyle: { color: '#409eff' } },
      { name: '关注', type: 'line', smooth: true, symbolSize: 6, data: list.map((x) => x.subscribe), itemStyle: { color: '#67c23a' } },
      { name: '取关', type: 'line', smooth: true, symbolSize: 6, data: list.map((x) => x.unsubscribe), itemStyle: { color: '#e6a23c' } }
    ]
  })
}

function renderPie(list) {
  const chart = ensurePieChart()
  if (!chart) return

  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}：{c} 条（{d}%）' },
    legend: { bottom: 0, itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 12 } },
    series: [
      {
        type: 'pie',
        radius: ['48%', '70%'],
        center: ['50%', '44%'],
        // 后端返回的就是 { name, value }，可直接赋值
        data: list,
        label: { formatter: '{b}\n{c} 条', fontSize: 11, color: '#606266' },
        labelLine: { length: 8, length2: 8 }
      }
    ]
  })
}

function resizeCharts() {
  trendChart.value?.resize()
  pieChart.value?.resize()
}

async function load() {
  loading.value = true
  try {
    const [ov, trend, dist] = await Promise.all([
      fetchOverview(),
      fetchTrend(7),
      fetchTypeDistribution()
    ])

    overview.value = ov

    // 等 DOM 更新完再初始化图表，否则容器尺寸是 0，ECharts 会画不出来
    await nextTick()
    renderTrend(trend)
    renderPie(dist)
  } catch {
    // 提示已由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  // 必须销毁，否则切换路由后实例仍挂在已卸载的 DOM 上，造成内存泄漏
  trendChart.value?.dispose()
  pieChart.value?.dispose()
})
</script>

<template>
  <div v-loading="loading">
    <!-- 概览卡片 -->
    <el-row :gutter="12">
      <el-col v-for="c in cards" :key="c.key" :xs="12" :sm="8" :md="4">
        <div class="stat-card">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-value" :style="{ color: c.accent }">{{ c.value }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表 -->
    <el-row :gutter="12" class="chart-row">
      <el-col :xs="24" :md="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>最近 7 天消息趋势</span>
              <el-button link type="primary" @click="load">刷新</el-button>
            </div>
          </template>
          <div ref="trendEl" class="chart chart-trend"></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header"><span>消息类型分布</span></div>
          </template>
          <div ref="pieEl" class="chart chart-pie"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert
      class="tip"
      type="info"
      :closable="false"
      show-icon
      title="统计口径说明"
      description="「累计关注 / 取关」统计的是消息流水里的发生次数，不是当前粉丝数 —— 同一个人多次关注会被计多次。真正的粉丝数需要单独的用户表（尚未建）。净增关注 = 关注 − 取关，可能为负。"
    />
  </div>
</template>

<style scoped>
.stat-card {
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 12px;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 24px;
  font-weight: 500;
  line-height: 1.2;
}

.chart-row {
  margin-top: 4px;
}

.chart-card {
  margin-bottom: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.chart {
  width: 100%;
}

.chart-trend {
  height: 300px;
}

.chart-pie {
  height: 300px;
}

.tip {
  margin-top: 4px;
}
</style>
