<template>
  <main class="auth-page">
    <section class="auth-shell">
      <div class="auth-brand">
        <span class="brand-mark">N</span>
        <div>
          <strong>NexusMind</strong>
          <small>客户支持中心</small>
        </div>
      </div>

      <div class="segmented" role="tablist" aria-label="账号操作">
        <button :class="{ active: mode === 'login' }" type="button" @click="mode = 'login'">登录</button>
        <button :class="{ active: mode === 'register' }" type="button" @click="mode = 'register'">注册</button>
      </div>

      <div class="auth-heading">
        <h1>{{ mode === 'login' ? '欢迎回来' : '创建用户账号' }}</h1>
        <p>{{ mode === 'login' ? '登录后继续处理你的咨询。' : '注册后即可开始咨询并保留会话记录。' }}</p>
      </div>

      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="username" autocomplete="username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item v-if="mode === 'register'" label="显示名称">
          <el-input v-model="displayName" autocomplete="name" placeholder="怎么称呼你" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" autocomplete="current-password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item v-if="mode === 'register'" label="确认密码">
          <el-input v-model="confirmPassword" type="password" autocomplete="new-password" show-password placeholder="再次输入密码" />
        </el-form-item>
        <el-button class="submit-button" type="primary" native-type="submit" :loading="loading">
          {{ mode === 'login' ? '登录' : '创建账号' }}
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const mode = ref<'login' | 'register'>('login')
const username = ref('')
const displayName = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)

async function submit() {
  if (!username.value.trim() || !password.value) return ElMessage.warning('请完整填写账号信息')
  if (mode.value === 'register') {
    if (!displayName.value.trim()) return ElMessage.warning('请输入显示名称')
    if (password.value !== confirmPassword.value) return ElMessage.warning('两次输入的密码不一致')
  }
  loading.value = true
  try {
    if (mode.value === 'register') {
      await auth.register(username.value.trim(), displayName.value.trim(), password.value)
    } else {
      await auth.login(username.value.trim(), password.value)
    }
    await router.replace(auth.user?.role === 'VISITOR' ? '/visitor' : '/console/chat')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { min-height: 100vh; display: grid; place-items: center; padding: 32px 20px; background: #f5f5f7; }
.auth-shell { width: min(420px, 100%); padding: 36px; border: 1px solid #dedee3; border-radius: 8px; background: rgba(255,255,255,.92); box-shadow: 0 16px 45px rgba(0,0,0,.07); }
.auth-brand { display: flex; align-items: center; gap: 12px; margin-bottom: 32px; }
.brand-mark { display: grid; width: 42px; height: 42px; place-items: center; border-radius: 8px; background: #1d1d1f; color: #fff; font-weight: 700; }
.auth-brand strong, .auth-brand small { display: block; }
.auth-brand strong { font-size: 17px; }
.auth-brand small { margin-top: 3px; color: #6e6e73; }
.segmented { display: grid; grid-template-columns: 1fr 1fr; padding: 3px; border-radius: 7px; background: #ececf0; }
.segmented button { height: 34px; border: 0; border-radius: 5px; background: transparent; color: #6e6e73; cursor: pointer; }
.segmented button.active { background: #fff; color: #1d1d1f; box-shadow: 0 1px 4px rgba(0,0,0,.1); }
.auth-heading { margin: 28px 0 22px; }
.auth-heading h1 { margin: 0; font-size: 28px; font-weight: 650; }
.auth-heading p { margin: 8px 0 0; color: #6e6e73; line-height: 1.55; }
.submit-button { width: 100%; height: 42px; margin-top: 6px; }
@media (max-width: 520px) { .auth-shell { padding: 26px 22px; } }
</style>
