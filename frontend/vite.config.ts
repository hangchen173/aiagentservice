import { createLogger, defineConfig, type Logger, type LogOptions } from 'vite'
import vue from '@vitejs/plugin-vue'

const viteLogger = createLogger()
const customLogger: Logger = {
  ...viteLogger,
  warn(message: string, options?: LogOptions) {
    if (!isIgnoredDependencyWarning(message)) {
      viteLogger.warn(message, options)
    }
  },
  warnOnce(message: string, options?: LogOptions) {
    if (!isIgnoredDependencyWarning(message)) {
      viteLogger.warnOnce(message, options)
    }
  }
}

function isIgnoredDependencyWarning(message: string) {
  return message.includes('[INVALID_ANNOTATION]') && message.includes('node_modules/@vueuse/core')
}

export default defineConfig({
  plugins: [vue()],
  customLogger,
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/vue') || id.includes('node_modules/vue-router') || id.includes('node_modules/pinia')) {
            return 'vue'
          }
          if (id.includes('node_modules/element-plus') || id.includes('node_modules/@element-plus/icons-vue')) {
            return 'element'
          }
          if (id.includes('node_modules/axios')) {
            return 'http'
          }
        }
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  }
})
