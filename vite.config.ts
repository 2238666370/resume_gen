import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  base: './',  // 相对路径，便于部署到任意子路径
  plugins: [
    react(),
  ],
  build: {
    rollupOptions: {
      output: {
        // 大依赖单独分包（R10-O8），配合路由懒加载减小首屏体积
        manualChunks: {
          'vendor-export': ['html2canvas', 'jspdf', 'html2pdf.js'],
        },
      },
    },
  },
})
