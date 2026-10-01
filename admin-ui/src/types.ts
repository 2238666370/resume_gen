export interface UserVO {
  id: string;
  username: string;
  role: string;
  nickname?: string;
  email?: string;
  status?: number;
  createdAt?: string;
}

export interface ResumeItem {
  id: string;
  title: string;
  templateId: string;
  updatedAt?: string;
  userId?: string;
  username?: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export interface StatsOverview {
  userCount: number;
  resumeCount: number;
  todayPv: number;
  todayUv: number;
  onlineUsers: number;
}

export interface TrendPoint {
  time: string;
  pv: number;
  uv: number;
}

export interface PvUv {
  pv: number;
  uv: number;
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

export interface TemplateItem {
  id: string;
  code: string;
  name: string;
  type: string;
  category?: string | null;
  ownerUserId?: string | null;
  useCount?: number;
  viewCount?: number;
  auditReason?: string | null;
  publishedAt?: string | null;
  sortOrder: number;
  status: number;
  createdAt?: string | null;
  /** 模板渲染 Schema（JSON 字符串），审核预览用。 */
  schema?: string | null;
  schemaVersion?: number;
}

export interface AdminPostItem {
  id: string;
  title: string;
  authorId: string;
  authorNickname?: string;
  auditStatus: number;
  auditReason?: string;
  likeCount: number;
  collectCount: number;
  commentCount: number;
  viewCount: number;
  reportCount: number;
  createdAt?: string;
}

export interface AdminReportItem {
  id: string;
  postId: string;
  postTitle?: string;
  reporterId: string;
  reporterNickname?: string;
  reason?: string;
  status: number;
  createdAt?: string;
}