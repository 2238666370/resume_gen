import axios from 'axios';
import type { AdminPostItem, AdminReportItem, CaptchaVO, LoginResponse, PageResult, PvUv, ResumeItem, StatsOverview, TemplateItem, TrendPoint, UserVO } from './types';

const baseURL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '';

export const client = axios.create({ baseURL, timeout: 15000 });

function redirectToLogin(): void {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  if (!window.location.hash.startsWith('#/login')) {
    window.location.hash = '#/login';
  }
}

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
      const err: any = new Error(body.message || '请求失败');
      err.code = body.code;
      return Promise.reject(err);
    }
    return body;
  },
  (error) => {
    const err: any = new Error(error?.message || '网络错误');
    err.code = error?.response?.status ?? -1;
    return Promise.reject(err);
  },
);

export const api = {
  getCaptcha() {
    return client.get('/api/auth/captcha') as Promise<CaptchaVO>;
  },
  login(username: string, password: string, captchaId: string, captchaCode: string) {
    return client.post('/api/auth/login', { username, password, captchaId, captchaCode }) as Promise<LoginResponse>;
  },
  logout() {
    return client.post('/api/auth/logout') as Promise<void>;
  },
  users(page: number, size: number, keyword?: string) {
    return client.get('/admin/users', { params: { page, size, keyword } }) as Promise<PageResult<UserVO>>;
  },
  setUserStatus(id: string, status: number) {
    return client.put(`/admin/users/${id}/status`, { status }) as Promise<void>;
  },
  setUserRole(id: string, role: string) {
    return client.put(`/admin/users/${id}/role`, { role }) as Promise<void>;
  },
  userResumes(id: string, page: number, size: number) {
    return client.get(`/admin/users/${id}/resumes`, { params: { page, size } }) as Promise<PageResult<ResumeItem>>;
  },
  resumes(page: number, size: number, keyword?: string) {
    return client.get('/admin/resumes', { params: { page, size, keyword } }) as Promise<PageResult<ResumeItem>>;
  },
  deleteResume(id: string) {
    return client.delete(`/admin/resumes/${id}`) as Promise<void>;
  },
  statsOverview() {
    return client.get('/admin/stats/overview') as Promise<StatsOverview>;
  },
  statsTrend(granularity: string, from?: string, to?: string) {
    return client.get('/admin/stats/trend', { params: { granularity, from, to } }) as Promise<TrendPoint[]>;
  },
  statsPvuv(from?: string, to?: string) {
    return client.get('/admin/stats/pvuv', { params: { from, to } }) as Promise<PvUv>;
  },
  communityPosts(page: number, size: number, status?: number) {
    return client.get('/admin/community/posts', { params: { page, size, status } }) as Promise<PageResult<AdminPostItem>>;
  },
  auditPost(id: string, status: number, reason?: string) {
    return client.put(`/admin/community/posts/${id}/audit`, { status, reason }) as Promise<void>;
  },
  deletePost(id: string) {
    return client.delete(`/admin/community/posts/${id}`) as Promise<void>;
  },
  communityReports(page: number, size: number) {
    return client.get('/admin/community/reports', { params: { page, size } }) as Promise<PageResult<AdminReportItem>>;
  },
  resolveReport(id: string) {
    return client.put(`/admin/community/reports/${id}`) as Promise<void>;
  },
  templates(page: number, size: number) {
    return client.get('/admin/templates', { params: { page, size } }) as Promise<PageResult<TemplateItem>>;
  },
  auditTemplate(id: string, approve: boolean, reason?: string) {
    return client.post(`/admin/templates/${id}/audit`, null, { params: { approve, reason } }) as Promise<TemplateItem>;
  },
  setTemplateStatus(id: string, status: number) {
    return client.put(`/admin/templates/${id}`, { status }) as Promise<TemplateItem>;
  },
  deleteTemplate(id: string) {
    return client.delete(`/admin/templates/${id}`) as Promise<void>;
  },
  decodeWatermark(file: File) {
    const form = new FormData();
    form.append('file', file);
    return client.post('/admin/watermark/decode', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }) as Promise<Record<string, unknown>>;
  },
};