import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'

const apiTarget = process.env.PAPER_AGENT_API_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [tailwindcss()],
  base: './',
  server: {
    port: 5173,
    proxy: {
      '/api': apiTarget
    }
  }
})
