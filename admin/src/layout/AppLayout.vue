<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, Tickets } from '@element-plus/icons-vue'

const route = useRoute()

// 让菜单高亮跟随当前路径。用 el-menu 的 router 模式，
// index 直接写路由路径，点击即跳转，不需要额外写 @select。
const activeMenu = computed(() => route.path)
</script>

<template>
  <el-container class="layout">
    <el-aside width="212px" class="aside">
      <div class="brand">
        <div class="brand-mark">W</div>
        <div>
          <div class="brand-title">wx-push</div>
          <div class="brand-sub">微信公众号管理后台</div>
        </div>
      </div>

      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/messages">
          <el-icon><Tickets /></el-icon>
          <span>消息记录</span>
        </el-menu-item>
      </el-menu>

      <div class="aside-footer">
        会话流水来自 <code>wx_message_log</code>
      </div>
    </el-aside>

    <el-container>
      <el-header height="60px" class="header">
        <div class="header-title">{{ route.meta.title }}</div>
        <el-tag size="small" type="info" effect="plain">订阅号 · 个人主体 · 未认证</el-tag>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  display: flex;
  flex-direction: column;
  background-color: #ffffff;
  border-right: 1px solid #e4e7ed;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid #f0f2f5;
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background-color: #409eff;
  color: #fff;
  font-weight: 500;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  line-height: 1.3;
}

.brand-sub {
  font-size: 11px;
  color: #909399;
  line-height: 1.3;
}

.menu {
  flex: 1;
  border-right: none;
}

.aside-footer {
  padding: 12px 16px;
  font-size: 11px;
  color: #a8abb2;
  border-top: 1px solid #f0f2f5;
}

.aside-footer code {
  font-size: 11px;
  color: #909399;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #ffffff;
  border-bottom: 1px solid #e4e7ed;
}

.header-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

.main {
  background-color: #f5f7fa;
  padding: 16px;
}
</style>
