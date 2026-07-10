import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElButton,
  ElDivider,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElLoading,
  ElMenu,
  ElMenuItem,
  ElSwitch,
  ElTable,
  ElTableColumn
} from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/main.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

app
  .use(createPinia())
  .use(router)
  .use(ElButton)
  .use(ElDivider)
  .use(ElEmpty)
  .use(ElForm)
  .use(ElFormItem)
  .use(ElInput)
  .use(ElInputNumber)
  .use(ElLoading)
  .use(ElMenu)
  .use(ElMenuItem)
  .use(ElSwitch)
  .use(ElTable)
  .use(ElTableColumn)
  .mount('#app')
