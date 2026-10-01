import type { ResumeData } from '../types/resume';

export interface UserVO {
  id: string;
  username: string;
  role: string;
  nickname?: string;
  email?: string;
  status?: number;
  createdAt?: string;
}

export interface LoginResponse {
  token: string;
  expiresIn: number;
  user: UserVO;
}

export interface CaptchaVO {
  captchaId: string;
  imageBase64: string;
}

export interface ResumeListItem {
  id: string;
  title: string;
  templateId: string;
  updatedAt?: string;
}

export interface ResumeDetail extends ResumeData {
  id: string;
  version: number;
  updatedAt?: string;
}

export interface ShareVO {
  id: string;
  shareKey: string;
  url?: string;
  hasPassword: boolean;
  showContact: boolean;
  expireAt?: string | null;
  viewCount: number;
  status: number;
  createdAt?: string;
}

export interface PublicShareVO {
  resume: ResumeDetail;
  showContact: boolean;
  viewCount: number;
}

export interface ProfileVO {
  name?: string;
  title?: string;
  phone?: string;
  email?: string;
  location?: string;
  website?: string;
  avatar?: string;
  summary?: string;
}

export interface TemplateVO {
  id: string;
  code: string;
  name: string;
  type: string;
  category?: string | null;
  schema?: string | null;
  thumbnail?: string | null;
  ownerUserId?: string | null;
  version?: number;
  schemaVersion?: number;
  useCount?: number;
  viewCount?: number;
  auditReason?: string | null;
  publishedAt?: string | null;
  sortOrder: number;
  status: number;
  createdAt?: string | null;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export interface CommunityPost {
  id: string;
  title: string;
  summary?: string;
  tags: string[];
  coverUrl?: string;
  authorId: string;
  authorNickname: string;
  authorAvatar?: string;
  likeCount: number;
  collectCount: number;
  commentCount: number;
  viewCount: number;
  createdAt?: string;
}

/** 我发布的帖子（含审核状态与原因）。 */
export interface MyPost extends CommunityPost {
  auditStatus: number;
  auditReason?: string;
}

export interface CommunityPostDetail extends CommunityPost {
  resume: ResumeDetail;
}

export interface CommunityComment {
  id: string;
  postId: string;
  userId: string;
  userNickname: string;
  userAvatar?: string;
  parentId?: string | null;
  content: string;
  createdAt?: string;
  children: CommunityComment[];
}

export interface Interaction {
  liked: boolean;
  collected: boolean;
  likeCount: number;
  collectCount: number;
}

export interface MyCommunity {
  posts: MyPost[];
  likes: CommunityPost[];
  collects: CommunityPost[];
}