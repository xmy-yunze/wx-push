<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { login as loginApi } from '../api/auth'
import { setUser } from '../store/auth'

/**
 * 登录页。
 *
 * 鉴权方案：Session + Cookie —— 登录成功后后端写入服务端会话，
 * 浏览器持有 JSESSIONID，axios 实例已开 withCredentials 自动携带。
 * 前端不需要自己管 token。
 *
 * 登录态由后端说了算：拿到用户信息后写入全局状态，路由守卫据此放行。
 */

const route = useRoute()
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
    const user = await loginApi(form.username, form.password)
    setUser(user)
    ElMessage.success('登录成功')

    // 回到被拦下来的那个页面；没有就去看板。
    // ⚠️ 只接受以 / 开头的站内路径：redirect 来自 URL 查询参数，是外部可控输入，
    // 不校验的话就成了一个「开放重定向」入口（可被用来伪造可信的跳转链接）。
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/dashboard'
    router.push(redirect)
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
        <el-form-item label="登录账号" prop="username">
          <el-input
            v-model="form.username"
            placeholder="登录账号（英文字母，如 admin）"
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
</style>
