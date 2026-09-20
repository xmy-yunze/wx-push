import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],

  server: {
    port: 5173,
    // 开发期把 /api 转发到后端。
    // 关键作用：浏览器看到的请求是 http://localhost:5173/api/...，属于「同源」，
    // 于是根本不会触发跨域检查 —— 这比在后端配 CORS 更省事，也是前端工程的标准做法。
    // （后端也配了 CORS 兜底，用于将来前后端分域部署的场景。）
    // ⚠️ 这里必须写 127.0.0.1，不能写 localhost。
    // 原因：Node 18+ 解析 localhost 时可能优先返回 IPv6 的 ::1，
    // 而 Spring Boot 的 Tomcat 监听的是 IPv4 —— 会直接连不上（ECONNREFUSED）。
    // 这类问题排查起来很费时间，写成明确的 IPv4 地址最省心。
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },

  build: {
    // 产物由 Nginx 托管；分开存放便于运维定位
    outDir: 'dist',
    chunkSizeWarningLimit: 1500
  }
})
