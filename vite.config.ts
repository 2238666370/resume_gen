import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  base: './',  // 相对路径，Electron file:// 加载需要
  plugins: [
    react(),
  ],
})
