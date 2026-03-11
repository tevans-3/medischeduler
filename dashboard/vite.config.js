import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  envDir: '../',
  server: {
    proxy: {
      '/api/auth': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/client-id': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/matches': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/running': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/assignments': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/assignments': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        ws: true,
      },
    },
  },
})
