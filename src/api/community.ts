import { client } from './client';
import type {
  CommunityComment,
  CommunityPost,
  CommunityPostDetail,
  Interaction,
  MyCommunity,
  MyPost,
  PageResult,
} from './types';

export interface PublishPostPayload {
  resumeId: string;
  title: string;
  summary?: string;
  coverUrl?: string;
  tags?: string[];
}

export function listPosts(
  page: number,
  size: number,
  tag?: string,
  keyword?: string,
  sort?: string,
): Promise<PageResult<CommunityPost>> {
  return client.get('/api/public/community/posts', {
    params: { page, size, tag, keyword, sort },
  }) as Promise<PageResult<CommunityPost>>;
}

export function getPost(id: string): Promise<CommunityPostDetail> {
  return client.get(`/api/public/community/posts/${id}`) as Promise<CommunityPostDetail>;
}

export function listComments(id: string): Promise<CommunityComment[]> {
  return client.get(`/api/public/community/posts/${id}/comments`) as Promise<CommunityComment[]>;
}

export function publishPost(payload: PublishPostPayload): Promise<MyPost> {
  return client.post('/api/community/posts', payload) as Promise<MyPost>;
}

export function getInteraction(id: string): Promise<Interaction> {
  return client.get(`/api/community/posts/${id}/interaction`) as Promise<Interaction>;
}

export function likePost(id: string): Promise<Interaction> {
  return client.post(`/api/community/posts/${id}/like`) as Promise<Interaction>;
}

export function collectPost(id: string): Promise<Interaction> {
  return client.post(`/api/community/posts/${id}/collect`) as Promise<Interaction>;
}

export function addComment(id: string, content: string, parentId?: string): Promise<CommunityComment> {
  return client.post(`/api/community/posts/${id}/comments`, { content, parentId }) as Promise<CommunityComment>;
}

export function deleteComment(id: string): Promise<void> {
  return client.delete(`/api/community/comments/${id}`) as Promise<void>;
}

export function reportPost(id: string, reason: string): Promise<void> {
  return client.post(`/api/community/posts/${id}/report`, { reason }) as Promise<void>;
}

export function myCommunity(): Promise<MyCommunity> {
  return client.get('/api/community/me') as Promise<MyCommunity>;
}