import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const LoginView = () => import('../views/LoginView.vue')
const VisitorView = () => import('../views/VisitorView.vue')
const ConsoleView = () => import('../views/ConsoleView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/visitor' },
    { path: '/login', component: LoginView },
    { path: '/visitor', component: VisitorView },
    { path: '/console/:tab?', component: ConsoleView }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path.startsWith('/console')) {
    if (!auth.token) {
      return '/login'
    }
    if (!auth.hasAnyRole(['ADMIN', 'AGENT'])) {
      return '/visitor'
    }
  }
})

export default router
