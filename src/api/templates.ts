import { client } from './client';
import type { PageResult, TemplateVO } from './types';

export function listTemplates(): Promise<TemplateVO[]> {
  return client.get('/api/templates') as Promise<TemplateVO[]>;
}

export function getTemplate(code: string): Promise<TemplateVO> {
  return client.get(`/api/templates/detail/${code}`) as Promise<TemplateVO>;
}

// ---------------- 用户自定义模板（R8-A1） ----------------

export interface TemplateSavePayload {
  name: string;
  category?: string;
  schema: string;
  version?: number;
}

export function listMyTemplates(): Promise<TemplateVO[]> {
  return client.get('/api/templates/my') as Promise<TemplateVO[]>;
}

export function createTemplate(payload: TemplateSavePayload): Promise<TemplateVO> {
  return client.post('/api/templates', payload) as Promise<TemplateVO>;
}

export function updateTemplate(id: string, payload: TemplateSavePayload): Promise<TemplateVO> {
  return client.put(`/api/templates/${id}`, payload) as Promise<TemplateVO>;
}

export function deleteTemplate(id: string): Promise<void> {
  return client.delete(`/api/templates/${id}`) as Promise<void>;
}

export function publishTemplate(id: string): Promise<TemplateVO> {
  return client.post(`/api/templates/${id}/publish`) as Promise<TemplateVO>;
}

// ---------------- 模板市场（R8-A3） ----------------

export function listMarketTemplates(params: {
  page?: number;
  size?: number;
  keyword?: string;
  category?: string;
} = {}): Promise<PageResult<TemplateVO>> {
  const q = new URLSearchParams();
  q.set('page', String(params.page ?? 1));
  q.set('size', String(params.size ?? 20));
  if (params.keyword) q.set('keyword', params.keyword);
  if (params.category) q.set('category', params.category);
  return client.get(`/api/templates/market?${q.toString()}`) as Promise<PageResult<TemplateVO>>;
}

export function getMarketTemplate(id: string): Promise<TemplateVO> {
  return client.get(`/api/templates/market/${id}`) as Promise<TemplateVO>;
}

export function useTemplate(id: string): Promise<TemplateVO> {
  return client.post(`/api/templates/${id}/use`) as Promise<TemplateVO>;
}

// ---------------- schema 解析缓存 ----------------

let schemaCache: Record<string, string | null> | null = null;

/** 按模板编码获取渲染 schema（缓存，官方预设可离线回退）。 */
export async function getTemplateSchema(code: string): Promise<string | null> {
  if (!schemaCache) {
    schemaCache = {};
    try {
      const list = await listTemplates();
      for (const t of list) schemaCache[t.code] = t.schema ?? null;
    } catch {
      // 拉取失败：仅回退预设，返回 null 让调用方走 fallbackLayout
    }
  }
  return schemaCache[code] ?? null;
}

// ---------------- 一键用：待应用模板编码 ----------------

let pendingTemplateCode: string | null = null;

/** 市场「一键用」后暂存模板编码，新建简历时消费。 */
export function setPendingTemplate(code: string): void {
  pendingTemplateCode = code;
}

export function consumePendingTemplate(): string | null {
  const c = pendingTemplateCode;
  pendingTemplateCode = null;
  return c;
}
