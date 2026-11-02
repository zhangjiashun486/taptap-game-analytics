import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './assets/main.css'

// 创建 Vue 应用：注册路由 + 挂载到 index.html 的 #app
createApp(App).use(router).mount('#app')
