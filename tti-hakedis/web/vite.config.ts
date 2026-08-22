import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Çıktı jar'ın `static/` klasörüne gömülür: tek servis, tek port, CORS yok, nginx yok.
// `base: './'` — index.html varlıkları göreli ister; sunucu kökü değişse bile bağlantılar bozulmaz.
//
// `server.proxy` yalnızca `npm run dev` içindir: geliştirme sunucusu 5173'te, API 8081'de.
// Vekil sayesinde tarayıcı her ikisini de aynı köken sayar; oturum cookie'si (`SameSite=Strict`)
// ve CSRF token'ı geliştirmede de üretimdeki gibi çalışır.
export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: false,
      },
    },
  },
})
