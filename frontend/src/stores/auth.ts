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
    token: sessionStorage.getItem('token') || '',
    user: JSON.parse(sessionStorage.getItem('user') || 'null') as LoginResponse | null
  }),
  actions: {
    async login(username: string, password: string) {
      const data = await api<LoginResponse>(http.post('/auth/login', { username, password }))
      this.token = data.token
      this.user = data
      this.persist(data)
    },
    async register(username: string, displayName: string, password: string) {
      const data = await api<LoginResponse>(http.post('/auth/register', { username, displayName, password }))
      this.token = data.token
      this.user = data
      this.persist(data)
    },
    persist(data: LoginResponse) {
      sessionStorage.setItem('token', data.token)
      sessionStorage.setItem('user', JSON.stringify(data))
    },
    hasAnyRole(roles: string[]) {
      return Boolean(this.user?.role && roles.includes(this.user.role))
    },
    logout() {
      this.token = ''
      this.user = null
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('user')
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
