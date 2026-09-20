import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './assets/main.css'

const app = createApp(App)

// 全局中文语言包 —— 不配的话分页组件会显示 "Go to"、"items/page" 这类英文
app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.mount('#app')
