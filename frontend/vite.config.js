import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 并行开发时多个 worktree 各自起后端，用这个环境变量指过去
const API_TARGET = process.env.VITE_API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 后端接口统一挂在 /api 下，保留路径直通 8080
      '/api': {
        target: API_TARGET,
        changeOrigin: true,
      },
      // 上传的图片由后端静态目录提供，开发时同样直通后端
      '/files': {
        target: API_TARGET,
        changeOrigin: true,
      },
    },
  },
})
