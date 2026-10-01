import { client } from './client';
import type { ResumeData } from '../types/resume';

export interface AiTaskSubmitVO {
  taskId: string;
  queue: string;
  status: string;
}

export interface AiTaskResult {
  taskId: string;
  taskType: string;
  status: string; // PENDING | RUNNING | SUCCESS | FAILED
  result?: unknown;
  error?: string;
}

export interface InterviewQuestion {
  category: string;
  question: string;
  answer: string;
  tips: string;
}

export interface InterviewSet {
  id: string;
  resumeId: string;
  title: string;
  targetRole?: string;
  questions: InterviewQuestion[];
  createdAt?: string;
}

export interface ResumeRewriteResult {
  original: string;
  revised: string;
}

export interface ResumeExpandResult {
  original: string;
  expanded: string;
}

export interface ResumeSuggestion {
  section: string;
  issue: string;
  advice: string;
  priority: string;
}

// ---------- R5 面试题库 ----------

export function generateInterview(payload: {
  resumeId: string;
  targetRole?: string;
  jd?: string;
}): Promise<InterviewSet> {
  return client.post('/api/ai/interview/generate', payload) as Promise<InterviewSet>;
}

export function listInterviewSets(): Promise<InterviewSet[]> {
  return client.get('/api/ai/interview/sets') as Promise<InterviewSet[]>;
}

export function deleteInterviewSet(id: string): Promise<void> {
  return client.delete(`/api/ai/interview/${id}`) as Promise<void>;
}

export function renameInterviewSet(id: string, title: string): Promise<InterviewSet> {
  return client.put(`/api/ai/interview/${id}/title`, { title }) as Promise<InterviewSet>;
}

/** 异步提交生成题库任务，返回 taskId，结果通过 {@link streamTaskResult} 订阅。 */
export function generateInterviewAsync(payload: {
  resumeId: string;
  targetRole?: string;
  jd?: string;
}): Promise<AiTaskSubmitVO> {
  return client.post('/api/ai/interview/async', payload) as Promise<AiTaskSubmitVO>;
}

/**
 * 订阅异步任务完成结果（SSE 推送）。任务完成后触发 onDone 并自动关闭流。
 * 返回取消函数（组件卸载时调用可断开连接）。
 */
export function streamTaskResult(
  taskId: string,
  onDone: (task: AiTaskResult) => void,
  onError?: () => void,
): () => void {
  const base = (client.defaults.baseURL as string) ?? '';
  const token = localStorage.getItem('token') ?? '';
  const es = new EventSource(`${base}/api/ai/task/${taskId}/stream?token=${encodeURIComponent(token)}`);
  es.onmessage = (evt: MessageEvent) => {
    let task: AiTaskResult;
    try {
      task = JSON.parse(evt.data) as AiTaskResult;
    } catch {
      onError?.();
      es.close();
      return;
    }
    onDone(task);
    es.close();
  };
  es.onerror = () => {
    onError?.();
    es.close();
  };
  return () => es.close();
}

// ---------- R6 AI 润色/扩写/建议 ----------

export function aiRewrite(payload: {
  resumeId?: string;
  text: string;
  section?: string;
}): Promise<ResumeRewriteResult> {
  return client.post('/api/ai/resume/rewrite', payload) as Promise<ResumeRewriteResult>;
}

export function aiRewriteAsync(payload: {
  resumeId?: string;
  text: string;
  section?: string;
}): Promise<AiTaskSubmitVO> {
  return client.post('/api/ai/resume/rewrite/async', payload) as Promise<AiTaskSubmitVO>;
}

export function aiExpand(payload: {
  resumeId?: string;
  text: string;
  section?: string;
}): Promise<ResumeExpandResult> {
  return client.post('/api/ai/resume/expand', payload) as Promise<ResumeExpandResult>;
}

export function aiExpandAsync(payload: {
  resumeId?: string;
  text: string;
  section?: string;
}): Promise<AiTaskSubmitVO> {
  return client.post('/api/ai/resume/expand/async', payload) as Promise<AiTaskSubmitVO>;
}

export function aiSuggest(payload: { resumeId: string }): Promise<ResumeSuggestion[]> {
  return client.post('/api/ai/resume/suggest', payload) as Promise<ResumeSuggestion[]>;
}

export function aiSuggestAsync(payload: { resumeId: string }): Promise<AiTaskSubmitVO> {
  return client.post('/api/ai/resume/suggest/async', payload) as Promise<AiTaskSubmitVO>;
}

// ---------- R6 全文改稿（生成改进稿，用于 diff 确认） ----------

export function aiImprove(payload: { resumeId: string }): Promise<ResumeData> {
  return client.post('/api/ai/resume/improve', payload) as Promise<ResumeData>;
}

export function aiImproveAsync(payload: { resumeId: string }): Promise<AiTaskSubmitVO> {
  return client.post('/api/ai/resume/improve/async', payload) as Promise<AiTaskSubmitVO>;
}

// ---------- R8-B 简历评分 + JD 匹配度 ----------

export interface ResumeScoreDimension {
  key: string;
  name: string;
  score: number;
  comment?: string;
}

export interface ResumeScoreResult {
  totalScore: number;
  matchedPercent: number;
  dimensions: ResumeScoreDimension[];
  skillHits: string[];
  missingSkills: string[];
  suggestions: string[];
}

export function aiScore(payload: {
  resumeId: string;
  targetRole?: string;
  jd?: string;
}): Promise<ResumeScoreResult> {
  return client.post('/api/ai/resume/score', payload) as Promise<ResumeScoreResult>;
}

export function aiScoreAsync(payload: {
  resumeId: string;
  targetRole?: string;
  jd?: string;
}): Promise<AiTaskSubmitVO> {
  return client.post('/api/ai/resume/score/async', payload) as Promise<AiTaskSubmitVO>;
}

// ---------- AI 历史记录 ----------

export interface AiHistoryRecord {
  id: string;
  resumeId?: string | null;
  resumeTitle?: string | null;
  taskType: string;
  summary?: string;
  output?: string;
  tokenUsage?: number;
  createdAt?: string;
}

export function listAiHistory(taskType?: string, limit?: number): Promise<AiHistoryRecord[]> {
  const params = new URLSearchParams();
  if (taskType) params.set('taskType', taskType);
  if (limit) params.set('limit', String(limit));
  const q = params.toString();
  return client.get(`/api/ai/history${q ? `?${q}` : ''}`) as Promise<AiHistoryRecord[]>;
}

export function getAiHistory(id: string): Promise<AiHistoryRecord> {
  return client.get(`/api/ai/history/${id}`) as Promise<AiHistoryRecord>;
}