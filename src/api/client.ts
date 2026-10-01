import axios from 'axios';

const baseURL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:8081';

/** 是否走「异步队列 + SSE 推送」，需与后端 ai.async.enabled=true 配套开启。 */
export const AI_ASYNC_ENABLED = import.meta.env.VITE_AI_ASYNC_ENABLED === 'true';

export interface ApiError extends Error {
  code?: number;
}

function redirectToLogin(): void {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  if (!window.location.hash.startsWith('#/login')) {
    window.location.hash = '#/login';
  }
}

export const client = axios.create({ baseURL, timeout: 15000 });

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response): any => {
    const body = response.data as { code?: number; message?: string; data?: unknown };
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data;
      }
      if (body.code === 401) {
        redirectToLogin();
      }
      const err: ApiError = new Error(body.message || '请求失败');
      err.code = body.code;
      return Promise.reject(err);
    }
    return body;
  },
  (error) => {
    const err: ApiError = new Error(error?.message || '网络错误');
    err.code = error?.response?.status ?? -1;
    return Promise.reject(err);
  },
);

/**
 * 将后端错误码映射为友好提示，供各页面统一展示。
 * 429 频控、熔断/超时等 5xx 会落到后端返回的具体 message，其余有固定文案兜底。
 */
export function getErrorMessage(err: unknown, fallback = '请求失败'): string {
  const code = (err as ApiError)?.code;
  if (code == null) {
    return (err as Error)?.message || fallback;
  }
  switch (code) {
    case 400:
      return '请求参数有误，请检查后重试';
    case 401:
      return '登录已失效，请重新登录';
    case 403:
      return '没有权限执行此操作';
    case 404:
      return '请求的资源不存在';
    case 409:
      return '数据冲突，请刷新后重试';
    default:
      // 429 频控 / 5xx（AI 繁忙、超时、熔断）等直接透传后端已本地化的 message
      return (err as Error)?.message || fallback;
  }
}