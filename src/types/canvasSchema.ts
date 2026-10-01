/**
 * 画布模板 Schema v2（R8-A4）。
 *
 * 约定：
 * - 坐标 / 尺寸单位 **mm**（A4 = 210 × 297mm）；
 * - 字号单位 **pt**（1pt = 1/72in，A4 = 595 × 842pt）；
 * - 与 v1（`TemplateSchema`，结构化板块）并存，由 `schemaVersion` 分派渲染器。
 */

/* ───────────── 单位与页面 ───────────── */

/** 1mm 对应的 px（96dpi）。 */
export const MM_TO_PX = 96 / 25.4;
/** 1pt 对应的 px。 */
export const PT_TO_PX = 96 / 72;
/** 1mm 对应的 pt。 */
export const MM_TO_PT = 72 / 25.4;

export const A4_WIDTH_MM = 210;
export const A4_HEIGHT_MM = 297;

export const mmToPx = (mm: number): number => mm * MM_TO_PX;
export const ptToPx = (pt: number): number => pt * PT_TO_PX;

/* ───────────── 元素模型 ───────────── */

export type CanvasElementType =
  | 'text' | 'field' | 'heading' | 'list'
  | 'image' | 'shape' | 'group' | 'pageBreak';

/** 元素类型白名单（前端渲染 + 后端校验共用同一集合）。 */
export const CANVAS_ELEMENT_TYPES: CanvasElementType[] = [
  'text', 'field', 'heading', 'list', 'image', 'shape', 'group', 'pageBreak',
];

export interface CanvasBind {
  /** 绑定路径，必须命中绑定白名单。列表元素绑定到数组，itemTemplate 内绑定到条目字段。 */
  path: string;
  /** 数据缺失时的兜底文案。 */
  fallback?: string;
}

export interface CanvasStyle {
  fontFamily?: string;
  /** 字号（pt）。 */
  fontSize?: number;
  fontWeight?: number;
  italic?: boolean;
  color?: string;
  lineHeight?: number;
  letterSpacing?: number;
  textAlign?: 'left' | 'center' | 'right';
  verticalAlign?: 'top' | 'middle' | 'bottom';
  /** 内边距（mm）。 */
  padding?: number;
  background?: string;
  opacity?: number;
  border?: { width: number; color: string; radius: number };
}

export interface CanvasElementBase {
  id: string;
  /** 图层名。 */
  name?: string;
  /** 相对页面左上角（mm）。 */
  x: number;
  y: number;
  /** 设计时尺寸（mm）。 */
  w: number;
  h: number;
  zIndex: number;
  locked?: boolean;
  hidden?: boolean;
  style?: CanvasStyle;
}

export interface CanvasTextElement extends CanvasElementBase {
  type: 'text';
  value: string;
}

export interface CanvasFieldElement extends CanvasElementBase {
  type: 'field';
  bind: CanvasBind;
  prefix?: string;
  suffix?: string;
}

export interface CanvasHeadingElement extends CanvasElementBase {
  type: 'heading';
  value: string;
  decoration?: 'plain' | 'bar' | 'underline' | 'capsule';
}

export interface CanvasListElement extends CanvasElementBase {
  type: 'list';
  /** 绑定到数组字段，如 `experience`。 */
  bind: CanvasBind;
  /** 条目间距（mm）。 */
  itemGap?: number;
  divider?: boolean;
  /** itemTemplate 内元素使用「组内相对坐标」，绑定路径相对条目对象。 */
  itemTemplate: CanvasChildElement[];
}

export interface CanvasImageElement extends CanvasElementBase {
  type: 'image';
  /** 仅允许头像字段或外链；本地图片（base64）不支持。 */
  source: 'avatar' | 'url';
  url?: string;
  fit?: 'cover' | 'contain';
  /** 圆角（mm）。 */
  radius?: number;
}

export interface CanvasShapeElement extends CanvasElementBase {
  type: 'shape';
  shape: 'rect' | 'ellipse' | 'line';
  fill?: string;
  stroke?: string;
}

export interface CanvasGroupElement extends CanvasElementBase {
  type: 'group';
  /** 组内元素使用相对坐标。 */
  children: CanvasChildElement[];
}

/** 手动分页符（本期不做自动分页）。 */
export interface CanvasPageBreakElement extends CanvasElementBase {
  type: 'pageBreak';
}

/** 可作为子元素的类型（group 内不再嵌套 group，校验层限制嵌套 ≤ 2）。 */
export type CanvasChildElement =
  | CanvasTextElement | CanvasFieldElement | CanvasHeadingElement
  | CanvasListElement | CanvasImageElement | CanvasShapeElement
  | CanvasGroupElement | CanvasPageBreakElement;

export type CanvasElement = CanvasChildElement;

/* ───────────── 页面与主题 ───────────── */

export interface CanvasPage {
  width: number;
  height: number;
  unit: 'mm';
  margin: { top: number; right: number; bottom: number; left: number };
}

export interface CanvasTheme {
  fontFamily?: string;
  /** 基准字号（pt）。 */
  baseFontSize?: number;
  palette?: { primary?: string; text?: string; muted?: string; line?: string };
}

export interface CanvasSchema {
  schemaVersion: 2;
  layout: 'canvas';
  page: CanvasPage;
  theme?: CanvasTheme;
  elements: CanvasElement[];
}

/* ───────────── 绑定路径白名单 ───────────── */

/** 顶层（页面级）可绑定路径。数组字段用于 `list` 元素。 */
export const ROOT_BIND_PATHS = [
  'personal.name', 'personal.title', 'personal.email', 'personal.phone',
  'personal.location', 'personal.website', 'personal.avatar', 'personal.summary',
  'education', 'experience', 'internship', 'skills',
  'projects', 'certificates', 'languages', 'customSections',
] as const;

/** 列表 `itemTemplate` 内可绑定路径（相对条目对象）。key 为数组字段名。 */
export const ITEM_BIND_PATHS: Record<string, readonly string[]> = {
  education: ['school', 'degree', 'major', 'startDate', 'endDate', 'gpa', 'description'],
  experience: ['company', 'position', 'startDate', 'endDate', 'current', 'description'],
  internship: ['company', 'position', 'startDate', 'endDate', 'current', 'description'],
  skills: ['name', 'level'],
  projects: ['name', 'role', 'startDate', 'endDate', 'description', 'link'],
  certificates: ['name', 'issuer', 'date', 'link'],
  languages: ['name', 'level'],
  customSections: ['title', 'content'],
};

export function isRootBindPath(path: string): boolean {
  return (ROOT_BIND_PATHS as readonly string[]).includes(path);
}

export function isItemBindPath(arrayPath: string, path: string): boolean {
  return (ITEM_BIND_PATHS[arrayPath] ?? []).includes(path);
}

/* ───────────── 默认值 ───────────── */

export const DEFAULT_PAGE: CanvasPage = {
  width: A4_WIDTH_MM,
  height: A4_HEIGHT_MM,
  unit: 'mm',
  margin: { top: 14, right: 14, bottom: 14, left: 14 },
};

export const DEFAULT_THEME: CanvasTheme = {
  fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
  baseFontSize: 10.5,
  palette: { primary: '#2563eb', text: '#111827', muted: '#6b7280', line: '#e5e7eb' },
};

/* ───────────── 解析与判定 ───────────── */

/** 判定是否为画布 Schema（v2）。 */
export function isCanvasSchema(value: unknown): value is CanvasSchema {
  const s = value as CanvasSchema | null;
  return !!s && typeof s === 'object' && s.layout === 'canvas' && Array.isArray(s.elements);
}

/** 解析 schema JSON 字符串，非画布 schema 返回 null。 */
export function parseCanvasSchema(json: string | null | undefined): CanvasSchema | null {
  if (!json) return null;
  try {
    const obj = JSON.parse(json) as unknown;
    return isCanvasSchema(obj) ? obj : null;
  } catch {
    return null;
  }
}

/** 数据路径取值（`a.b.c`）。 */
export function resolvePath(root: unknown, path: string): unknown {
  if (root == null || !path) return undefined;
  return path.split('.').reduce<unknown>((acc, key) => {
    if (acc == null) return undefined;
    return (acc as Record<string, unknown>)[key];
  }, root);
}

/** 将绑定值渲染成文本（数组/对象返回空串）。 */
export function bindToText(value: unknown, fallback = ''): string {
  if (value === null || value === undefined) return fallback;
  if (typeof value === 'string') return value || fallback;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return fallback;
}

let seq = 0;
export const nextElementId = (prefix = 'el'): string => `${prefix}_${Date.now().toString(36)}_${++seq}`;

/** 空白画布（P1 新建模板起点）。 */
export function createDefaultCanvasSchema(): CanvasSchema {
  const primary = DEFAULT_THEME.palette?.primary ?? '#2563eb';
  return {
    schemaVersion: 2,
    layout: 'canvas',
    page: { ...DEFAULT_PAGE, margin: { ...DEFAULT_PAGE.margin } },
    theme: { ...DEFAULT_THEME, palette: { ...DEFAULT_THEME.palette } },
    elements: [
      {
        id: nextElementId('field'), type: 'field', name: '姓名',
        x: 20, y: 18, w: 80, h: 10, zIndex: 1,
        bind: { path: 'personal.name', fallback: '姓名' },
        style: { fontSize: 20, fontWeight: 700, color: '#111827' },
      },
      {
        id: nextElementId('field'), type: 'field', name: '求职意向',
        x: 20, y: 29, w: 100, h: 6, zIndex: 2,
        bind: { path: 'personal.title', fallback: '求职意向' },
        style: { fontSize: 11, color: primary },
      },
      {
        id: nextElementId('heading'), type: 'heading', name: '工作经历标题',
        x: 20, y: 44, w: 170, h: 7, zIndex: 3,
        value: '工作经历', decoration: 'bar',
        style: { fontSize: 11, fontWeight: 700, color: primary },
      },
      {
        id: nextElementId('list'), type: 'list', name: '工作经历',
        x: 20, y: 54, w: 170, h: 30, zIndex: 4,
        bind: { path: 'experience' }, itemGap: 4, divider: false,
        itemTemplate: [
          {
            id: nextElementId('f'), type: 'field', name: '职位',
            x: 0, y: 0, w: 105, h: 5, zIndex: 1,
            bind: { path: 'position', fallback: '职位' },
            style: { fontSize: 10.5, fontWeight: 600 },
          },
          {
            id: nextElementId('f'), type: 'field', name: '单位',
            x: 105, y: 0, w: 65, h: 5, zIndex: 2,
            bind: { path: 'company' },
            style: { fontSize: 9.5, color: primary, textAlign: 'right' },
          },
          {
            id: nextElementId('f'), type: 'field', name: '描述',
            x: 0, y: 5.5, w: 170, h: 12, zIndex: 3,
            bind: { path: 'description' },
            style: { fontSize: 9, color: '#4b5563' },
          },
        ],
      },
    ],
  };
}
