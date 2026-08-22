import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Panel API'ye göreli yolla gidiyor (src/api/client.ts). Üretimde bu ayrımı Caddy yapıyor;
  // geliştirmede aynı yolları buradaki proxy backend'e taşıyor, böylece dev ve üretim aynı
  // kodu çalıştırıyor ve CORS iki ortamda da gerekmiyor.
  server: {
    proxy: {
      '/api': 'http://localhost:8000',
      '/admin': 'http://localhost:8000',
      '/docs': 'http://localhost:8000',
      '/openapi.json': 'http://localhost:8000',
      '/static': 'http://localhost:8000',
      '/health': 'http://localhost:8000',
    },
  },
})
