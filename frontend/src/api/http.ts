import axios, { AxiosError } from 'axios'
import { useAuthStore } from '../stores/auth'

export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
}

export const http = axios.create({
  baseURL: '/api'
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401 && !String(error.config?.url || '').includes('/auth/login')) {
      useAuthStore().logout()
      if (window.location.pathname !== '/login') window.location.assign('/login')
    }
    return Promise.reject(error)
  }
)

export async function api<T>(promise: Promise<{ data: ApiResponse<T> }>) {
  try {
    const response = await promise
    if (!response.data.success) {
      throw new Error(response.data.message)
    }
    return response.data.data
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const axiosError = error as AxiosError<ApiResponse<unknown>>
      throw new Error(axiosError.response?.data?.message || axiosError.message || '请求失败')
    }
    throw error
  }
}
