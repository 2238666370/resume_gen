import type { CanvasElement, CanvasFieldElement, CanvasGroupElement, CanvasListElement, CanvasShapeElement, CanvasStyle } from '../types/canvasSchema';
import { DEFAULT_THEME, nextElementId } from '../types/canvasSchema';

/**
 * 画布「固定块」预设（R8-A4）。
 *
 * 固定块 = 绑定到既有简历表 1:1 的列表块，条目内的元素可自由摆放（itemTemplate 坐标系）。
 * 不在固定集合内的新块一律走「自定义板块」，绑定 `customSections`（标题 + 正文）。
 */

const TEXT_PILL: CanvasStyle = { fontSize: 10.5, fontWeight: 600 };
const TEXT_META: CanvasStyle = { fontSize: 9, color: '#6b7280' };
const TEXT_BODY: CanvasStyle = { fontSize: 9, color: '#4b5563' };

/** 构造绑定到既有字段的元素（坐标为条目内相对坐标，单位 mm）。 */
export function fieldElement(
  path: string,
  x: number,
  y: number,
  w: number,
  h: number,
  z: number,
  style: CanvasStyle,
  fallback?: string,
  name?: string,
): CanvasFieldElement {
  return {
    id: nextElementId('f'),
    type: 'field',
    name,
    x, y, w, h,
    zIndex: z,
    bind: { path, fallback },
    style,
  };
}

/** 各数组字段的条目模板（key 与绑定路径一致）。 */
export const BLOCK_ITEM_TEMPLATES: Record<string, (w: number) => CanvasElement[]> = {
  experience: (w) => [
    fieldElement('position', 0, 0, w * 0.62, 5, 1, TEXT_PILL, '职位'),
    fieldElement('company', w * 0.62, 0, w * 0.38, 5, 2, { ...TEXT_META, textAlign: 'right' }),
    fieldElement('description', 0, 5.5, w, 12, 3, TEXT_BODY),
  ],
  internship: (w) => BLOCK_ITEM_TEMPLATES.experience(w),
  education: (w) => [
    fieldElement('school', 0, 0, w * 0.62, 5, 1, TEXT_PILL, '学校名称'),
    fieldElement('endDate', w * 0.62, 0, w * 0.38, 5, 2, { ...TEXT_META, textAlign: 'right' }),
    fieldElement('degree', 0, 5.5, w, 5, 3, TEXT_META),
    fieldElement('description', 0, 10.5, w, 10, 4, TEXT_BODY),
  ],
  skills: (w) => [fieldElement('name', 0, 0, w, 5, 1, { fontSize: 9.5 })],
  projects: (w) => [
    fieldElement('name', 0, 0, w * 0.62, 5, 1, TEXT_PILL, '项目名称'),
    fieldElement('endDate', w * 0.62, 0, w * 0.38, 5, 2, { ...TEXT_META, textAlign: 'right' }),
    fieldElement('role', 0, 5.5, w, 5, 3, TEXT_META),
    fieldElement('description', 0, 10.5, w, 10, 4, TEXT_BODY),
  ],
  certificates: (w) => [
    fieldElement('name', 0, 0, w * 0.6, 5, 1, { fontSize: 9.5 }),
    fieldElement('issuer', w * 0.6, 0, w * 0.4, 5, 2, { ...TEXT_META, textAlign: 'right' }),
  ],
  languages: (w) => [
    fieldElement('name', 0, 0, w * 0.5, 5, 1, { fontSize: 9.5 }),
    fieldElement('level', w * 0.5, 0, w * 0.5, 5, 2, { ...TEXT_META, textAlign: 'right' }),
  ],
  customSections: (w) => [
    fieldElement('title', 0, 0, w, 5, 1, { fontSize: 10, fontWeight: 600 }, '自定义板块'),
    fieldElement('content', 0, 5.5, w, 12, 2, TEXT_BODY),
  ],
};

/** 条目模板高度（子元素底部最大值）。 */
export const templateHeightOf = (tpl: CanvasElement[]): number =>
  tpl.reduce((max, child) => Math.max(max, child.y + child.h), 0);

/** 组件库中的固定块（与 resume_* 表一一对应）。 */
export interface FixedBlock {
  path: string;
  label: string;
}

export const FIXED_BLOCKS: FixedBlock[] = [
  { path: 'experience', label: '工作经历' },
  { path: 'internship', label: '实习经历' },
  { path: 'education', label: '教育经历' },
  { path: 'skills', label: '专业技能' },
  { path: 'projects', label: '项目经历' },
  { path: 'certificates', label: '证书荣誉' },
  { path: 'languages', label: '语言能力' },
];

/** 自定义板块（新块一律走这里）：绑定 customSections，内容仅 标题 + 正文。 */
export const CUSTOM_BLOCK_PATH = 'customSections';

/* ───────────── 区块预设库（组合元素，一键落版） ───────────── */

export interface BlockPreset {
  id: string;
  label: string;
  hint: string;
}

export const BLOCK_PRESETS: BlockPreset[] = [
  { id: 'header-left', label: '头部 · 左对齐', hint: '姓名 / 求职意向 / 联系方式' },
  { id: 'header-center', label: '头部 · 居中头像', hint: '头像居中 + 姓名职位' },
  { id: 'section-title', label: '标题 + 分隔线', hint: '板块标题与横线组合' },
  { id: 'summary-block', label: '个人简介块', hint: '标题 + 简介正文' },
];

const AVATAR_SIZE = 24;

function groupOf(
  name: string,
  x: number,
  y: number,
  w: number,
  h: number,
  z: number,
  children: CanvasElement[],
): CanvasGroupElement {
  return { id: nextElementId('g'), type: 'group', name, x, y, w, h, zIndex: z, children };
}

const lineElement = (w: number, y: number, z: number, color = '#e5e7eb'): CanvasShapeElement => ({
  id: nextElementId('s'),
  type: 'shape',
  name: '分隔线',
  x: 0, y, w, h: 0, zIndex: z,
  shape: 'line',
  fill: color,
});

/**
 * 按 id 生成预设组合（整体包成一个 group，便于一次性移动/缩放，可解组后逐元素微调）。
 * 预设内不包含 `list`（校验层禁止列表嵌套在组内）。
 */
export function buildPreset(
  id: string,
  opts: { x: number; y: number; w: number; z: number },
): CanvasGroupElement | null {
  const { x, y, w, z } = opts;
  const primary = DEFAULT_THEME.palette?.primary ?? '#2563eb';
  const meta: CanvasStyle = { fontSize: 9, color: '#6b7280' };

  switch (id) {
    case 'header-left':
      return groupOf('头部 · 左对齐', x, y, w, 25, z, [
        fieldElement('personal.name', 0, 0, w - 40, 10, 1, { fontSize: 20, fontWeight: 700 }, '姓名', '姓名'),
        fieldElement('personal.title', 0, 11, w - 40, 6, 2, { fontSize: 11, color: primary }, '求职意向', '求职意向'),
        fieldElement('personal.email', 0, 19, w * 0.5, 5, 3, meta),
        fieldElement('personal.phone', w * 0.5, 19, w * 0.5, 5, 4, meta),
      ]);

    case 'header-center':
      return groupOf('头部 · 居中头像', x, y, w, 45, z, [
        {
          id: nextElementId('image'), type: 'image', name: '头像',
          x: (w - AVATAR_SIZE) / 2, y: 0, w: AVATAR_SIZE, h: AVATAR_SIZE, zIndex: 1,
          source: 'avatar', fit: 'cover', radius: AVATAR_SIZE / 2,
        },
        fieldElement('personal.name', 0, 26, w, 10, 2, { fontSize: 20, fontWeight: 700, textAlign: 'center' }, '姓名', '姓名'),
        fieldElement('personal.title', 0, 37, w, 6, 3, { fontSize: 11, color: primary, textAlign: 'center' }, '求职意向', '求职意向'),
      ]);

    case 'section-title':
      return groupOf('标题 + 分隔线', x, y, w, 10, z, [
        {
          id: nextElementId('heading'), type: 'heading', name: '板块标题',
          x: 0, y: 0, w, h: 7, zIndex: 1,
          value: '板块标题', decoration: 'bar',
          style: { fontSize: 11, fontWeight: 700, color: primary },
        },
        lineElement(w, 8, 2),
      ]);

    case 'summary-block':
      return groupOf('个人简介块', x, y, w, 26, z, [
        {
          id: nextElementId('heading'), type: 'heading', name: '个人简介标题',
          x: 0, y: 0, w, h: 7, zIndex: 1,
          value: '个人简介', decoration: 'bar',
          style: { fontSize: 11, fontWeight: 700, color: primary },
        },
        fieldElement('personal.summary', 0, 9, w, 16, 2, { fontSize: 9.5, color: '#4b5563' }, '', '个人简介'),
      ]);

    default:
      return null;
  }
}

/** 按绑定路径构造列表块。 */
export function createListElement(opts: {
  path: string;
  name: string;
  x: number;
  y: number;
  w: number;
  z: number;
  itemGap?: number;
}): CanvasListElement {
  const build = BLOCK_ITEM_TEMPLATES[opts.path] ?? BLOCK_ITEM_TEMPLATES.customSections;
  const itemTemplate = build(opts.w);
  // 设计高度按 3 条预估，运行时按真实条目（含板块显隐）再推挤
  const h = templateHeightOf(itemTemplate) * 3 + 8;
  return {
    id: nextElementId('list'),
    type: 'list',
    name: opts.name,
    x: opts.x,
    y: opts.y,
    w: opts.w,
    h,
    zIndex: opts.z,
    bind: { path: opts.path },
    itemGap: opts.itemGap ?? 4,
    divider: false,
    itemTemplate,
  };
}
