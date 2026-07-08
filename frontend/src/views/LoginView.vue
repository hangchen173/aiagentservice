<template>
  <div class="page login-page">
    <div class="login-box">
      <h1>NexusMind 多智能体客服中枢</h1>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="账号">
          <el-input v-model="username" placeholder="admin / agent / visitor" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading">登录</el-button>
      </el-form>
      <p>管理员：admin / admin123，客服：agent / agent123，访客：visitor / visitor123</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('admin')
const password = ref('admin123')
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    router.push(username.value === 'visitor' ? '/visitor' : '/console/chat')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: grid;
  place-items: center;
  padding: 24px;
}

.login-box {
  width: min(420px, 100%);
  background: #ffffff;
  border: 1px solid #dce2ea;
  border-radius: 8px;
  padding: 28px;
}

h1 {
  margin: 0 0 24px;
  color: #16324f;
  font-size: 24px;
}

p {
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}
</style>
