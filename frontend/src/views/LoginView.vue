<template>
  <main class="auth-page">
    <section class="auth-shell">
      <div class="auth-brand">
        <span class="brand-mark"><i></i></span>
        <div>
          <strong>NexusMind</strong>
          <small>LIVE SUPPORT / 01</small>
        </div>
      </div>

      <div class="segmented" role="tablist" aria-label="账号操作">
        <button :class="{ active: mode === 'login' }" type="button" @click="mode = 'login'">登录</button>
        <button :class="{ active: mode === 'register' }" type="button" @click="mode = 'register'">注册</button>
      </div>

      <div class="auth-heading">
        <span class="auth-kicker">BACKSTAGE ACCESS</span>
        <h1>{{ mode === 'login' ? '欢迎回来' : '加入支持频道' }}</h1>
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
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 20px;
  background:
    linear-gradient(90deg, rgba(10, 5, 8, .96), rgba(18, 7, 12, .72) 48%, rgba(9, 5, 8, .9)),
    linear-gradient(0deg, rgba(9, 5, 8, .45), rgba(9, 5, 8, .12)),
    url('/assets/stage-red.jpg') center / cover fixed;
}
.auth-page::before { position: fixed; inset: 18px; border: 1px solid rgba(222, 187, 159, .2); pointer-events: none; content: ""; }
.auth-shell { position: relative; width: min(430px, 100%); padding: 38px; border: 1px solid rgba(222, 187, 159, .28); border-radius: 6px; background: rgba(20, 13, 17, .9); box-shadow: 0 24px 80px rgba(0,0,0,.5); backdrop-filter: blur(16px); }
.auth-shell::after { position: absolute; inset: 8px; border: 1px solid rgba(222, 187, 159, .08); pointer-events: none; content: ""; }
.auth-brand { position: relative; z-index: 1; display: flex; align-items: center; gap: 14px; margin-bottom: 30px; }
.brand-mark { display: grid; width: 42px; height: 42px; place-items: center; border: 1px solid #d8b58c; transform: rotate(45deg); }
.brand-mark i { width: 16px; height: 16px; border: 1px solid #d8b58c; background: #a92746; }
.auth-brand strong, .auth-brand small { display: block; }
.auth-brand strong { font-family: Georgia, "Times New Roman", serif; font-size: 20px; font-weight: 500; }
.auth-brand small { margin-top: 4px; color: #d8b58c; font-size: 10px; }
.segmented { position: relative; z-index: 1; display: grid; grid-template-columns: 1fr 1fr; padding: 3px; border: 1px solid rgba(222,187,159,.14); border-radius: 5px; background: rgba(5,3,5,.42); }
.segmented button { height: 34px; border: 0; border-radius: 3px; background: transparent; color: #a99b9e; cursor: pointer; }
.segmented button.active { background: #8f213b; color: #fff; box-shadow: 0 5px 18px rgba(95, 9, 32, .35); }
.auth-heading { position: relative; z-index: 1; margin: 28px 0 22px; }
.auth-kicker { color: #d8b58c; font-size: 10px; font-weight: 700; }
.auth-heading h1 { margin: 8px 0 0; font-family: Georgia, "Songti SC", serif; font-size: 30px; font-weight: 500; }
.auth-heading p { margin: 8px 0 0; color: #a99b9e; line-height: 1.55; }
.auth-shell :deep(.el-form) { position: relative; z-index: 1; }
.auth-shell :deep(.el-form-item__label) { color: #cbbec0; }
.submit-button { width: 100%; height: 42px; margin-top: 6px; }
@media (max-width: 520px) { .auth-page { padding: 24px 14px; }.auth-page::before { inset: 8px; }.auth-shell { padding: 28px 22px; } }
</style>
