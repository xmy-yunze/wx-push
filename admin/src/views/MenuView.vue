<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus, Refresh, Upload, View } from '@element-plus/icons-vue'
import { clearMenu, fetchMenu, publishMenu } from '../api/menu'

/**
 * 自定义菜单管理页。
 *
 * ⚠️ 两个方向要分清：
 *   - 「刷新」= 从微信拉线上真实状态（只读）；
 *   - 编辑区 = 我们本地的**草稿**，改完必须点「发布」才会真正生效。
 * 菜单不落库，所以「发布」成功后应以微信返回的状态为准，而不是信任本地草稿。
 *
 * ⚠️ 未认证订阅号的硬限制（页面上也提示了）：
 *   - 菜单不能跳外部网址（微信返回 45058），所以默认全部用 click 类型；
 *   - click 点击后由后端 MenuCatalog 决定回复什么，key 必须与后端保持一致。
 */

/** 微信规定的结构上限 */
const MAX_TOP = 3
const MAX_CHILD = 5

const draft = ref([])
const payloadJson = ref('')
const remoteButtons = ref([])
const loading = ref(false)
const busy = ref(false)

/**
 * 与后端 MenuCatalog 对应的默认菜单。
 * ⚠️ 后端才是真相源，这里只是给运营者一个可编辑的起点 —— 改后端时记得同步。
 */
function defaultDraft() {
  return [
    { name: '关于我', kind: 'click', key: 'MENU_ABOUT', url: '', children: [] },
    {
      name: '更多',
      kind: 'sub',
      key: '',
      url: '',
      children: [
        { name: '怎么用', kind: 'click', key: 'MENU_HOWTO', url: '', children: [] },
        { name: '联系我', kind: 'click', key: 'MENU_CONTACT', url: '', children: [] }
      ]
    },
    { name: '当前进度', kind: 'click', key: 'MENU_PROGRESS', url: '', children: [] }
  ]
}

/** 微信返回的按钮 → 草稿节点 */
function fromWechat(button) {
  if (Array.isArray(button.sub_button)) {
    return {
      name: button.name || '',
      kind: 'sub',
      key: '',
      url: '',
      children: button.sub_button.map(fromWechat)
    }
  }
  if (button.type === 'view') {
    return { name: button.name || '', kind: 'view', key: '', url: button.url || '', children: [] }
  }
  return { name: button.name || '', kind: 'click', key: button.key || '', url: '', children: [] }
}

/** 草稿节点 → 微信要求的报文结构 */
function toWechat(node) {
  if (node.kind === 'sub') {
    return { name: node.name, sub_button: node.children.map(toWechat) }
  }
  if (node.kind === 'view') {
    return { type: 'view', name: node.name, url: node.url }
  }
  return { type: 'click', name: node.name, key: node.key }
}

/** 组装成提交给后端的完整报文 */
function buildPayload() {
  return { button: draft.value.map(toWechat) }
}

const canAddTop = computed(() => draft.value.length < MAX_TOP)

function addTop() {
  if (!canAddTop.value) {
    ElMessage.warning(`一级菜单最多 ${MAX_TOP} 个`)
    return
  }
  draft.value.push({ name: '', kind: 'click', key: '', url: '', children: [] })
}

function removeTop(index) {
  draft.value.splice(index, 1)
  payloadJson.value = ''
}

function addChild(parent) {
  if (parent.children.length >= MAX_CHILD) {
    ElMessage.warning(`二级菜单最多 ${MAX_CHILD} 个`)
    return
  }
  parent.children.push({ name: '', kind: 'click', key: '', url: '', children: [] })
}

function removeChild(parent, index) {
  parent.children.splice(index, 1)
  payloadJson.value = ''
}

/**
 * 切换一级项的类型。
 *
 * 从「叶子」切成「二级菜单」时补一个空子项 —— 否则会得到一个没有孩子的容器，
 * 后端校验会直接拒绝（微信也不允许空容器）。
 */
function onKindChange(node) {
  if (node.kind === 'sub' && node.children.length === 0) {
    node.children.push({ name: '', kind: 'click', key: '', url: '', children: [] })
  }
  if (node.kind !== 'sub') {
    node.children = []
  }
  payloadJson.value = ''
}

/** 发布前的本地自检，把能提前发现的问题说清楚（后端仍会再校验一次） */
function validateDraft() {
  if (draft.value.length === 0) {
    return '菜单不能为空，至少要有一个一级菜单'
  }
  for (const node of draft.value) {
    if (!node.name || !node.name.trim()) {
      return '有菜单项还没填名称'
    }
    if (node.kind === 'sub') {
      if (node.children.length === 0) {
        return `「${node.name}」下面至少要有一个二级菜单`
      }
      for (const child of node.children) {
        if (!child.name || !child.name.trim()) {
          return `「${node.name}」下面有二级菜单没填名称`
        }
        if (child.kind === 'click' && !child.key) {
          return `二级菜单「${child.name}」还没填 key`
        }
      }
    }
    if (node.kind === 'click' && !node.key) {
      return `「${node.name}」还没填 key（后端靠它决定回复内容）`
    }
    if (node.kind === 'view' && !node.url) {
      return `「${node.name}」还没填链接`
    }
  }
  return null
}

async function refreshRemote() {
  loading.value = true
  try {
    const data = await fetchMenu()
    remoteButtons.value = data?.menu?.button || []
    if (remoteButtons.value.length > 0) {
      // 拉到线上结构就顺手灌进编辑器：多数场景是「在现有菜单上小改」
      draft.value = remoteButtons.value.map(fromWechat)
      payloadJson.value = ''
    }
  } catch {
    // 错误提示已由 axios 拦截器统一处理（例如 40164 会直接显示白名单提示）
  } finally {
    loading.value = false
  }
}

function loadDefault() {
  draft.value = defaultDraft()
  payloadJson.value = ''
  ElMessage.success('已载入默认菜单草稿（还没发布，点「预览」或「发布」才会生效）')
}

async function preview() {
  const problem = validateDraft()
  if (problem) {
    ElMessage.warning(problem)
    return
  }
  busy.value = true
  try {
    const result = await publishMenu(buildPayload(), { dryRun: true })
    payloadJson.value = JSON.stringify(JSON.parse(result.payloadJson), null, 2)
    ElMessage.success('校验通过 —— 下面是将要发给微信的报文（未发送）')
  } catch {
    // 拦截器已提示
  } finally {
    busy.value = false
  }
}

async function publish() {
  const problem = validateDraft()
  if (problem) {
    ElMessage.warning(problem)
    return
  }
  try {
    await ElMessageBox.confirm('发布后会覆盖公众号当前的菜单，确定继续吗？', '确认发布', {
      confirmButtonText: '发布',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 用户取消
  }

  busy.value = true
  try {
    const result = await publishMenu(buildPayload())
    payloadJson.value = JSON.stringify(JSON.parse(result.payloadJson), null, 2)
    ElMessage.success('发布成功（微信端生效可能有短暂延迟）')
    await refreshRemote()
  } catch {
    // 拦截器已提示 —— 例如 40164 会直接显示「IP 不在白名单内」的完整说明
  } finally {
    busy.value = false
  }
}

async function onClear() {
  try {
    await ElMessageBox.confirm('清空后公众号菜单栏会变成空的，确定继续吗？', '确认清空', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  busy.value = true
  try {
    await clearMenu()
    ElMessage.success('线上菜单已清空')
    await refreshRemote()
  } catch {
    // 拦截器已提示
  } finally {
    busy.value = false
  }
}

onMounted(refreshRemote)
</script>

<template>
  <div class="menu-page">
    <!-- ⚠️ 这个前置条件不满足时，下面所有操作都会失败，所以放在最显眼处 -->
    <el-alert type="warning" :closable="false" show-icon class="tip">
      <template #title>发布前必须先把当前公网出口 IP 加入公众号后台的 IP 白名单</template>
      <template #default>
        <div class="tip-body">
          否则微信会返回 <code>40164</code>。位置：公众平台 → 设置与开发 → 基本配置 → IP 白名单。
          用手机热点上网时公网 IP 会变，每次都要重新添加。
        </div>
      </template>
    </el-alert>

    <el-card shadow="never" class="toolbar">
      <div class="toolbar-inner">
        <div class="toolbar-left">
          <el-button :icon="Refresh" :loading="loading" @click="refreshRemote">刷新线上菜单</el-button>
          <el-button @click="loadDefault">载入默认菜单</el-button>
          <span class="sep">|</span>
          <el-button :icon="View" :loading="busy" @click="preview">预览报文</el-button>
          <el-button type="primary" :icon="Upload" :loading="busy" @click="publish">发布到微信</el-button>
          <el-button type="danger" plain :icon="Delete" :loading="busy" @click="onClear">清空菜单</el-button>
        </div>
        <div class="toolbar-right">
          <el-tag size="small" type="info" effect="plain">
            一级 {{ draft.length }}/{{ MAX_TOP }}
          </el-tag>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>菜单编辑器（草稿）</span>
              <el-button size="small" type="primary" plain :icon="Plus" :disabled="!canAddTop" @click="addTop">
                添加一级菜单
              </el-button>
            </div>
          </template>

          <el-empty v-if="draft.length === 0" description="还没有菜单 —— 可以「刷新线上菜单」拉取，或「载入默认菜单」从模板开始" />

          <div v-for="(node, index) in draft" :key="index" class="node">
            <div class="node-head">
              <span class="node-index">一级 {{ index + 1 }}</span>
              <el-button size="small" text type="danger" :icon="Delete" @click="removeTop(index)">删除</el-button>
            </div>

            <el-form label-width="72px" label-position="left">
              <el-form-item label="类型">
                <el-radio-group v-model="node.kind" size="small" @change="onKindChange(node)">
                  <el-radio-button value="click">点击回复</el-radio-button>
                  <el-radio-button value="sub">二级菜单</el-radio-button>
                  <el-radio-button value="view">跳转链接</el-radio-button>
                </el-radio-group>
              </el-form-item>

              <el-form-item label="名称">
                <el-input v-model="node.name" size="small" placeholder="一级最多 4 个汉字（或 8 个字母）" maxlength="8" />
              </el-form-item>

              <el-form-item v-if="node.kind === 'click'" label="key">
                <el-input v-model="node.key" size="small" placeholder="如 MENU_ABOUT，需与后端 MenuCatalog 一致" />
              </el-form-item>

              <el-form-item v-if="node.kind === 'view'" label="链接">
                <el-input v-model="node.url" size="small" placeholder="只能填公众号内页面（mp.weixin.qq.com），外链会报 45058" />
              </el-form-item>
            </el-form>

            <div v-if="node.kind === 'sub'" class="children">
              <div class="children-head">
                <span>二级菜单（{{ node.children.length }}/{{ MAX_CHILD }}）</span>
                <el-button size="small" text type="primary" :icon="Plus" @click="addChild(node)">添加</el-button>
              </div>

              <div v-for="(child, ci) in node.children" :key="ci" class="child">
                <el-input v-model="child.name" size="small" placeholder="名称（最多 8 个汉字）" class="child-name" />
                <el-input v-model="child.key" size="small" placeholder="key，如 MENU_HOWTO" class="child-key" />
                <el-button size="small" text type="danger" :icon="Delete" @click="removeChild(node, ci)" />
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never" class="preview-card">
          <template #header>
            <div class="card-head">
              <span>报文预览</span>
              <span class="hint">点「预览报文」或「发布」后显示</span>
            </div>
          </template>
          <pre v-if="payloadJson" class="json">{{ payloadJson }}</pre>
          <el-empty v-else description="还没有生成报文" :image-size="80" />

          <div class="remote">
            <div class="remote-title">
              线上当前生效的菜单（来自微信）
              <el-tag v-if="remoteButtons.length === 0" size="small" type="info" effect="plain">空</el-tag>
            </div>
            <pre v-if="remoteButtons.length > 0" class="json small">{{
              JSON.stringify({ menu: { button: remoteButtons } }, null, 2)
            }}</pre>
            <div v-else class="remote-empty">公众号当前没有配置菜单</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.menu-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tip-body {
  line-height: 1.6;
}

.tip code {
  font-size: 12px;
}

.toolbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.sep {
  color: #dcdfe6;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.node {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}

.node:last-child {
  margin-bottom: 0;
}

.node-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.node-index {
  font-size: 12px;
  color: #909399;
}

.children {
  margin-top: 4px;
  padding: 10px;
  background-color: #fafafa;
  border-radius: 6px;
}

.children-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.child {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.child:last-child {
  margin-bottom: 0;
}

.child-name {
  flex: 1;
}

.child-key {
  flex: 1.2;
}

.preview-card {
  height: 100%;
}

.hint {
  font-size: 12px;
  color: #909399;
}

.json {
  margin: 0;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #303133;
  overflow: auto;
  max-height: 320px;
  white-space: pre-wrap;
  word-break: break-all;
}

.json.small {
  max-height: 200px;
}

.remote {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
}

.remote-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.remote-empty {
  font-size: 12px;
  color: #a8abb2;
}
</style>
