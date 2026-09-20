<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import request from '../api/request'

/**
 * 登录页。
 *
 * ⚠️ 状态说明：后端鉴权接口（POST /api/auth/login）**尚未实现**，
 * 契约已在 docs/admin-接口契约.md 第四节定好。
 * 本页按契约把前端部分做完 —— 接口一上线即可直接跑通，前端不需要再改。
 *
 * 鉴权方案：Session + Cookie，因此 axios 实例已开 withCredentials，
 * 登录成功后浏览器会自动持有 JSESSIONID，后续请求自动带上。
 */

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

async function onSubmit() {
  // validate 校验不通过会 reject，这里不 try/catch，
  // 让它自然中断提交（Element Plus 会在表单项上标红）
  await formRef.value.validate()

  loading.value = true
  try {
    await request.post('/auth/login', { ...form })
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch {
    // 错误提示已由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-head">
        <div class="login-mark">W</div>
        <div class="login-title">wx-push 管理后台</div>
        <div class="login-sub">微信公众号消息管理</div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="账号" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入账号"
            size="large"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="login-btn"
          :loading="loading"
          @click="onSubmit"
        >
          登 录
        </el-button>
      </el-form>

      <el-alert
        class="login-tip"
        type="warning"
        :closable="false"
        show-icon
        title="后端鉴权接口尚未实现"
        description="POST /api/auth/login 还没写（契约见 docs/admin-接口契约.md 第四节）。现在点登录会返回 404，属预期现象。"
      />
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background-color: #f0f2f5;
}

.login-card {
  width: 380px;
  padding: 32px 32px 24px;
  background-color: #ffffff;
  border-radius: 12px;
  border: 1px solid #ebeef5;
}

.login-head {
  text-align: center;
  margin-bottom: 24px;
}

.login-mark {
  width: 44px;
  height: 44px;
  margin: 0 auto 12px;
  border-radius: 10px;
  background-color: #409eff;
  color: #fff;
  font-size: 20px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-title {
  font-size: 17px;
  font-weight: 500;
  color: #303133;
}

.login-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.login-btn {
  width: 100%;
  margin-top: 4px;
}

.login-tip {
  margin-top: 20px;
}
</style>
