import { client } from './client';
import type { PageResult, ResumeDetail, ResumeListItem } from './types';
import type { ResumeData } from '../types/resume';

export function listResumes(page: number, size: number, keyword?: string): Promise<PageResult<ResumeListItem>> {
  return client.get('/api/resumes', { params: { page, size, keyword } }) as Promise<PageResult<ResumeListItem>>;
}

export function createResume(title: string, templateId?: string): Promise<ResumeDetail> {
  return client.post('/api/resumes', { title, templateId }) as Promise<ResumeDetail>;
}

export function getResume(id: string): Promise<ResumeDetail> {
  return client.get(`/api/resumes/${id}`) as Promise<ResumeDetail>;
}

export function updateResume(id: string, data: ResumeData, version?: number): Promise<ResumeDetail> {
  return client.put(`/api/resumes/${id}`, data, { params: { version } }) as Promise<ResumeDetail>;
}

export function deleteResume(id: string): Promise<void> {
  return client.delete(`/api/resumes/${id}`) as Promise<void>;
}

// ---------- R8-C 版本历史 / 时光机 ----------

export interface ResumeSnapshot {
  id: string;
  resumeId: string;
  version: number;
  source: string;
  createdAt?: string;
  content?: ResumeData | null;
}

export interface ResumeVersionDiff {
  fromVersion: number;
  toVersion: number;
  from: ResumeData;
  to: ResumeData;
}

export function listHistory(resumeId: string): Promise<ResumeSnapshot[]> {
  return client.get(`/api/resumes/${resumeId}/history`) as Promise<ResumeSnapshot[]>;
}

export function getHistory(resumeId: string, snapshotId: string): Promise<ResumeSnapshot> {
  return client.get(`/api/resumes/${resumeId}/history/${snapshotId}`) as Promise<ResumeSnapshot>;
}

export function diffResumeVersions(
  resumeId: string,
  from: string,
  to: string,
): Promise<ResumeVersionDiff> {
  return client.get(`/api/resumes/${resumeId}/diff`, { params: { from, to } }) as Promise<ResumeVersionDiff>;
}

export function rollbackResume(resumeId: string, snapshotId: string): Promise<ResumeDetail> {
  return client.post(`/api/resumes/${resumeId}/rollback/${snapshotId}`) as Promise<ResumeDetail>;
}