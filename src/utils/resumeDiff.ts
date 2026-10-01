import type { ResumeData } from '../types/resume';

/**
 * 简历全文 diff：对比「当前稿」与「AI 改进稿」，产出逐字段改动点（用于逐条接受/拒绝）。
 */

export interface ResumeDiffItem {
  /** 唯一键（path 的 JSON 序列化），用于接受集合。 */
  key: string;
  path: (string | number)[];
  label: string;
  oldText: string;
  revisedText: string;
  rawOld: unknown;
  rawRevised: unknown;
}

const SECTION_LABELS: Record<string, string> = {
  personal: '个人信息',
  education: '教育经历',
  experience: '工作经历',
  internship: '实习经历',
  skills: '技能',
  projects: '项目经历',
  certificates: '证书',
  languages: '语言',
  customSections: '自定义板块',
};

const FIELD_LABELS: Record<string, string> = {
  title: '标题',
  name: '姓名',
  summary: '个人简介',
  email: '邮箱',
  phone: '电话',
  location: '所在地',
  website: '个人网站',
  avatar: '头像',
  school: '学校',
  degree: '学历',
  major: '专业',
  startDate: '开始时间',
  endDate: '结束时间',
  gpa: 'GPA',
  company: '公司',
  position: '职位',
  current: '在职',
  description: '描述',
  role: '角色',
  link: '链接',
  issuer: '颁发机构',
  date: '日期',
  level: '等级',
  content: '内容',
};

function isPlainObject(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function display(v: unknown): string {
  if (v === null || v === undefined) return '';
  if (typeof v === 'string') return v;
  if (typeof v === 'number' || typeof v === 'boolean') return String(v);
  return JSON.stringify(v);
}

function makeLabel(path: (string | number)[]): string {
  const sectionKey = String(path[0] ?? '');
  const section = SECTION_LABELS[sectionKey] ?? sectionKey;
  let itemNo: number | undefined;
  for (let i = 1; i < path.length; i++) {
    const seg = path[i];
    if (typeof seg === 'number') {
      itemNo = seg;
      break;
    }
  }
  const last = path[path.length - 1];
  const field = typeof last === 'string' ? FIELD_LABELS[last] ?? last : '';
  const suffix = itemNo !== undefined ? ` #${itemNo + 1}` : '';
  return field ? `${section}${suffix} · ${field}` : `${section}${suffix}`;
}

function walk(
  oldV: unknown,
  newV: unknown,
  path: (string | number)[],
  out: ResumeDiffItem[],
): void {
  if (isPlainObject(oldV) && isPlainObject(newV)) {
    const keys = new Set([...Object.keys(oldV), ...Object.keys(newV)]);
    for (const k of keys) {
      if (k === 'id') continue;
      walk(oldV[k], newV[k], [...path, k], out);
    }
    return;
  }
  if (Array.isArray(oldV) && Array.isArray(newV)) {
    if (oldV.length !== newV.length) {
      // 数组长度变化时整段作为一个改动点，避免逐项对齐歧义
      out.push({
        key: JSON.stringify(path),
        path,
        label: makeLabel(path),
        oldText: display(oldV),
        revisedText: display(newV),
        rawOld: oldV,
        rawRevised: newV,
      });
      return;
    }
    for (let i = 0; i < oldV.length; i++) {
      walk(oldV[i], newV[i], [...path, i], out);
    }
    return;
  }
  if (oldV !== newV && !(oldV === undefined && newV === undefined)) {
    out.push({
      key: JSON.stringify(path),
      path,
      label: makeLabel(path),
      oldText: display(oldV),
      revisedText: display(newV),
      rawOld: oldV,
      rawRevised: newV,
    });
  }
}

function deepClone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v)) as T;
}

function setAt(root: unknown, path: (string | number)[], value: unknown): void {
  // path 至少 1 段；根为对象/数组，按段逐层定位到父节点后写叶子
  let cur: any = root;
  for (let i = 0; i < path.length - 1; i++) {
    cur = cur[path[i]];
  }
  cur[path[path.length - 1]] = value;
}

/** 计算改动点，默认按文本字段（id 除外）逐一对齐数组元素。 */
export function diffResume(oldData: ResumeData, newData: ResumeData): ResumeDiffItem[] {
  const out: ResumeDiffItem[] = [];
  walk(oldData, newData, [], out);
  return out;
}

/** 判定改动是否为“内容非空”，用于默认接受策略（清空类改动默认不勾选）。 */
export function isEnhancing(item: ResumeDiffItem): boolean {
  const r = item.rawRevised;
  if (r === null || r === undefined) return false;
  if (typeof r === 'string') return r.trim().length > 0;
  if (typeof r === 'number' || typeof r === 'boolean') return true;
  return true;
}

/** 将已接受的改动点合并回「当前稿」，返回新的完整简历数据（未接受字段保持原值）。 */
export function applyAcceptedDiff(
  oldData: ResumeData,
  newData: ResumeData,
  acceptedKeys: Set<string>,
): ResumeData {
  const merged = deepClone(oldData);
  const items = diffResume(oldData, newData);
  for (const it of items) {
    if (acceptedKeys.has(it.key)) {
      setAt(merged, it.path, it.rawRevised);
    }
  }
  return merged;
}