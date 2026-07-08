import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import VisitorView from '../views/VisitorView.vue'
import ConsoleView from '../views/ConsoleView.vue'
import { useAuthStore } from '../stores/auth'

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
