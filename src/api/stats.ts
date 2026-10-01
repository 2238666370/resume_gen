import { client } from './client';
import type { CommunityComment } from './types';

export interface MyStatsOverview {
  resumeCount: number;
  pv: number;
  uv: number;
  likeCount: number;
  collectCount: number;
  commentCount: number;
  shareViews: number;
}

export interface MyResumeStat {
  id: string;
  title: string;
  updatedAt?: string;
  pv: number;
  uv: number;
  likeCount: number;
  collectCount: number;
  commentCount: number;
  shareViews: number;
}

export interface TrendPoint {
  time: string;
  pv: number;
  uv: number;
}

export function getMyStatsOverview(): Promise<MyStatsOverview> {
  return client.get('/api/stats/my/overview') as Promise<MyStatsOverview>;
}

export function getMyResumeStats(): Promise<MyResumeStat[]> {
  return client.get('/api/stats/my/resumes') as Promise<MyResumeStat[]>;
}

export function getResumeTrend(id: string, granularity?: string): Promise<TrendPoint[]> {
  return client.get(`/api/stats/my/resumes/${id}/trend`, {
    params: granularity ? { granularity } : undefined,
  }) as Promise<TrendPoint[]>;
}

export function getResumeComments(id: string): Promise<CommunityComment[]> {
  return client.get(`/api/stats/my/resumes/${id}/comments`) as Promise<CommunityComment[]>;
}
