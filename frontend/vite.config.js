import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    proxy: {
      '/api/chat/stream': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // SSE 专用代理配置：不压缩、不缓冲
        configure: (proxy) => {
          // 在请求发出前，移除 Accept-Encoding 防止后端压缩
          // 后端不压缩 → 代理不需要解压 → 数据直接流式转发
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.setHeader('Accept-Encoding', 'identity')
          })
          proxy.on('proxyRes', (proxyRes) => {
            // 强制关闭所有可能的缓冲
            proxyRes.headers['cache-control'] = 'no-cache, no-transform'
            proxyRes.headers['x-accel-buffering'] = 'no'
            proxyRes.headers['connection'] = 'keep-alive'
            // 移除压缩头，防止代理尝试解压缓冲
            delete proxyRes.headers['content-encoding']
          })
        }
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
