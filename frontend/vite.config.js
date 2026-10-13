import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    // 前端开发服务器端口。改为 5174 以避免与本机其他项目的 5173 冲突。
    port: 5174,
    // 若 5174 也被占用，自动尝试下一个可用端口（多项目并行时更省心）
    strictPort: false,
    proxy: {
      '/api': {
        // 必须与 backend/src/main/resources/application.yml 的 server.port 保持一致
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  }
})