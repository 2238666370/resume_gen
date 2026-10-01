import { client } from './client';
import type { PublicShareVO, ShareVO } from './types';

export interface ShareCreatePayload {
  resumeId: string;
  expireDays?: number;
  password?: string;
  showContact?: boolean;
}

export function createShare(payload: ShareCreatePayload): Promise<ShareVO> {
  return client.post('/api/shares', payload) as Promise<ShareVO>;
}

export function listShares(): Promise<ShareVO[]> {
  return client.get('/api/shares') as Promise<ShareVO[]>;
}

export function revokeShare(key: string): Promise<void> {
  return client.delete(`/api/shares/${key}`) as Promise<void>;
}

export function getPublicShare(key: string, password?: string): Promise<PublicShareVO> {
  return client.get(`/api/public/shares/${key}`, { params: { password } }) as Promise<PublicShareVO>;
}