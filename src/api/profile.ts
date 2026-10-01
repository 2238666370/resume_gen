import { client } from './client';
import type { ProfileVO } from './types';

export function getProfile(): Promise<ProfileVO> {
  return client.get('/api/profile') as Promise<ProfileVO>;
}

export function updateProfile(payload: ProfileVO): Promise<ProfileVO> {
  return client.put('/api/profile', payload) as Promise<ProfileVO>;
}