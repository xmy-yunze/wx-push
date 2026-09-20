<script setup>
import { onMounted, reactive, ref } from 'vue'
import { fetchMessages, fetchMessageDetail } from '../api/message'

/**
 * 消息记录页。
 *
 * 对接 GET /api/messages（分页列表）与 GET /api/messages/{id}（详情），
 * 字段与筛选参数严格按 docs/admin-接口契约.md 第二节。
 */

const loading = ref(false)
const rows = ref([])
const total = ref(0)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

// 查询条件。注意 dateRange 是前端专用的派生字段，
// 请求时要拆成后端的 startDate / endDate 两个参数（见 buildParams）。
const query = reactive({
  page: 1,
  size: 20,
  msgType: '',
  event: '',
  dateRange: [],
  keyword: ''
})

const MSG_TYPES = [
  { label: '文本 text', value: 'text' },
  { label: '事件 event', value: 'event' },
  { label: '图片 image', value: 'image' },
  { label: '语音 voice', value: 'voice' },
  { label: '视频 video', value: 'video' },
  { label: '位置 location', value: 'location' },
  { label: '链接 link', value: 'link' }
]

const EVENTS = [
  { label: '关注 subscribe', value: 'subscribe' },
  { label: '取关 unsubscribe', value: 'unsubscribe' },
  { label: '菜单点击 CLICK', value: 'CLICK' },
  { label: '菜单跳转 VIEW', value: 'VIEW' }
]

/** 把页面上的查询条件翻译成后端接口的参数 */
function buildParams() {
  const params = { page: query.page, size: query.size }

  // 空字符串一律不传 —— 后端把「参数缺省」当作「不筛选」，
  // 如果传空串，会拼出 `msg_type = ''` 这种永远查不到数据的条件。
  if (query.msgType) params.msgType = query.msgType
  if (query.event) params.event = query.event
  if (query.keyword) params.keyword = query.keyword

  const [start, end] = query.dateRange || []
  if (start) params.startDate = start
  if (end) params.endDate = end

  return params
}

async function load() {
  loading.value = true
  try {
    const data = await fetchMessages(buildParams())
    rows.value = data.list
    total.value = data.total
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  // 条件变了必须回到第 1 页，否则可能停在一个已经超出范围的页码上，看到空列表
  query.page = 1
  load()
}

function onReset() {
  query.page = 1
  query.size = 20
  query.msgType = ''
  query.event = ''
  query.dateRange = []
  query.keyword = ''
  load()
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await fetchMessageDetail(row.id)
  } catch {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

/** 事件类型的中文说明，纯粹为了运营看得懂 */
function eventText(event) {
  const map = {
    subscribe: '关注',
    unsubscribe: '取关',
    CLICK: '菜单点击',
    VIEW: '菜单跳转'
  }
  return map[event] || event
}

onMounted(load)
</script>

<template>
  <div>
    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="消息类型">
          <el-select v-model="query.msgType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="t in MSG_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="事件类型">
          <el-select v-model="query.event" placeholder="全部" clearable style="width: 170px">
            <el-option v-for="e in EVENTS" :key="e.value" :label="e.label" :value="e.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="时间范围">
          <el-date-picker
            v-model="query.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 260px"
          />
        </el-form-item>

        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="匹配 openid 或消息内容"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" row-key="id" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />

        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="row.msgType === 'event' ? 'warning' : 'primary'" size="small" effect="plain">
              {{ row.msgType || '—' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="事件" width="110">
          <template #default="{ row }">
            <span v-if="row.event">{{ eventText(row.event) }}</span>
            <span v-else class="text-placeholder">—</span>
          </template>
        </el-table-column>

        <el-table-column prop="fromUser" label="发送者 openid" width="150" show-overflow-tooltip />

        <el-table-column label="内容" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <!-- content 对事件消息恒为 null，占位符比空白单元格更明确 -->
            <span v-if="row.content">{{ row.content }}</span>
            <span v-else class="text-placeholder">—</span>
          </template>
        </el-table-column>

        <el-table-column label="入库时间" width="170" class-name="nowrap">
          <template #default="{ row }">{{ row.createdAt }}</template>
        </el-table-column>

        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="没有符合条件的消息" :image-size="80" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="onSearch"
        />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="消息详情" size="480px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="1" border>
          <el-descriptions-item label="主键 ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="微信消息 ID">
            <span v-if="detail.msgId">{{ detail.msgId }}</span>
            <span v-else class="text-placeholder">— （事件消息没有此字段）</span>
          </el-descriptions-item>
          <el-descriptions-item label="消息类型">{{ detail.msgType }}</el-descriptions-item>
          <el-descriptions-item label="事件类型">
            <span v-if="detail.event">{{ eventText(detail.event) }}（{{ detail.event }}）</span>
            <span v-else class="text-placeholder">—</span>
          </el-descriptions-item>
          <el-descriptions-item label="发送者 openid">{{ detail.fromUser }}</el-descriptions-item>
          <el-descriptions-item label="公众号原始 ID">{{ detail.toUser }}</el-descriptions-item>
          <el-descriptions-item label="消息内容">
            <span v-if="detail.content">{{ detail.content }}</span>
            <span v-else class="text-placeholder">—</span>
          </el-descriptions-item>
          <el-descriptions-item label="入库时间">{{ detail.createdAt }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.filter-card {
  margin-bottom: 12px;
}

.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
