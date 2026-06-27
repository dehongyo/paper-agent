import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'

const apiTarget = process.env.PAPER_AGENT_API_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [tailwindcss()],
  base: './',
  server: {
    port: 5173,
    cors: {
      origin: '*',
      methods: '*',
      allowedHeaders: '*',
    },
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('origin');
            proxyReq.removeHeader('referer');
          });
        },
      }
    }
  }
})
