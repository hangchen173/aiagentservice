import { defineStore } from 'pinia'
import { api, http } from '../api/http'

export interface LoginResponse {
  token: string
  userId: number
  username: string
  displayName: string
  role: string
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null') as LoginResponse | null
  }),
  actions: {
    async login(username: string, password: string) {
      const data = await api<LoginResponse>(http.post('/auth/login', { username, password }))
      this.token = data.token
      this.user = data
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data))
    },
    async startVisitor() {
      if (this.token && this.user?.role === 'VISITOR') {
        return
      }
      const data = await api<LoginResponse>(http.post('/auth/visitor'))
      this.token = data.token
      this.user = data
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data))
    },
    hasAnyRole(roles: string[]) {
      return Boolean(this.user?.role && roles.includes(this.user.role))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
