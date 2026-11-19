import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 后端接口统一挂在 /api 下，保留路径直通 8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 上传的图片由后端静态目录提供，开发时同样直通 8080
      '/files': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
