import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// 每个用例结束后卸载已挂载的组件，避免 DOM 互相污染
afterEach(() => {
  cleanup();
  localStorage.clear();
});
