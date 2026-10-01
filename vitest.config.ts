import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// 前端单元测试配置（与 vite.config.ts 分离，避免影响生产构建）
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    css: false,
  },
});
