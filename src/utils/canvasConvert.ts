import type { SchemaSection, TemplateSchema } from '../types/schema';
import type { CanvasElement, CanvasListElement, CanvasSchema } from '../types/canvasSchema';
import { DEFAULT_PAGE, DEFAULT_THEME, nextElementId } from '../types/canvasSchema';
import { BLOCK_ITEM_TEMPLATES, fieldElement as field, templateHeightOf } from './canvasPresets';

/**
 * v1（结构化板块）→ v2（画布）转换器。
 *
 * 旧编辑器移除后，用户打开存量 v1 模板时先用本转换器生成一份画布初始稿，
 * 保存后即升级为 v2。转换是「尽力而为」的：保留板块顺序与全部数据绑定（复用固定块预设），
 * 但会退化为单栏自上而下排版（v1 的双栏与纹饰细节不迁移）。
 */

const TEXT_META = { fontSize: 9, color: '#6b7280' } as const;

const ARRAY_SECTIONS = new Set([
  'experience', 'internship', 'education', 'skills', 'projects', 'certificates', 'languages',
]);

const DEFAULT_TITLES: Record<string, string> = {
  summary: '个人简介',
  experience: '工作经历',
  internship: '实习经历',
  education: '教育经历',
  skills: '专业技能',
  projects: '项目经历',
  certificates: '证书荣誉',
  languages: '语言能力',
};

const templateOf = (path: string, w: number): CanvasElement[] =>
  (BLOCK_ITEM_TEMPLATES[path] ?? BLOCK_ITEM_TEMPLATES.customSections)(w);

/** 把 v1 结构化 Schema 转换为 v2 画布 Schema（单栏自上而下）。 */
export function convertV1ToCanvas(v1: TemplateSchema): CanvasSchema {
  const page = { ...DEFAULT_PAGE, margin: { ...DEFAULT_PAGE.margin } };
  const theme = { ...DEFAULT_THEME, palette: { ...DEFAULT_THEME.palette } };
  const primary = theme.palette?.primary ?? '#2563eb';
  const contentW = page.width - page.margin.left - page.margin.right;
  const themeV1 = v1.theme ?? {};
  const decoration = themeV1.titleStyle ?? 'bar';

  const elements: CanvasElement[] = [];
  let cursor = 0;
  let z = 1;

  /* ── 头部 ── */
  if (themeV1.showAvatar !== false) {
    elements.push({
      id: nextElementId('image'), type: 'image', name: '头像',
      x: contentW - 30, y: cursor, w: 30, h: 30, zIndex: z++,
      source: 'avatar', fit: 'cover', radius: 15,
    });
  }
  elements.push(field('personal.name', 0, cursor, contentW - 36, 10, z++, { fontSize: 20, fontWeight: 700 }, '姓名', '姓名'));
  cursor += 12;
  elements.push(field('personal.title', 0, cursor, contentW - 36, 6, z++, { fontSize: 11, color: primary }, '求职意向', '求职意向'));
  cursor += 8;

  if (themeV1.showContacts !== false) {
    elements.push(
      field('personal.email', 0, cursor, contentW * 0.5, 5, z++, TEXT_META),
      field('personal.phone', contentW * 0.5, cursor, contentW * 0.5, 5, z++, TEXT_META),
    );
    cursor += 7;
    elements.push(
      field('personal.location', 0, cursor, contentW * 0.5, 5, z++, TEXT_META),
      field('personal.website', contentW * 0.5, cursor, contentW * 0.5, 5, z++, TEXT_META),
    );
    cursor += 9;
  }

  /* ── 板块 ── */
  const sections: SchemaSection[] = (v1.sections ?? [])
    .filter((s) => s.show !== false && s.type !== 'personal');

  for (const section of sections) {
    const title = section.title || DEFAULT_TITLES[section.type] || String(section.type);

    elements.push({
      id: nextElementId('heading'), type: 'heading', name: `${title}·标题`,
      x: 0, y: cursor, w: contentW, h: 7, zIndex: z++,
      value: title, decoration,
      style: { fontSize: 11, fontWeight: 700, color: primary },
    });
    cursor += 9;

    if (section.type === 'summary') {
      elements.push(field('personal.summary', 0, cursor, contentW, 16, z++, { fontSize: 9.5, color: '#4b5563' }, '', '个人简介'));
      cursor += 21;
      continue;
    }

    let bindPath: string;
    if (section.type === 'custom') {
      bindPath = 'customSections';
    } else if (ARRAY_SECTIONS.has(section.type)) {
      bindPath = section.type;
    } else {
      continue;
    }

    const itemTemplate = templateOf(bindPath, contentW);
    const contentH = templateHeightOf(itemTemplate) * 3 + 8; // 按 3 条预估，运行时按真实条目推挤
    const list: CanvasListElement = {
      id: nextElementId('list'), type: 'list', name: title,
      x: 0, y: cursor, w: contentW, h: contentH, zIndex: z++,
      bind: { path: bindPath }, itemGap: 4, divider: false,
      itemTemplate,
    };
    elements.push(list);
    cursor += contentH + 5;
  }

  return { schemaVersion: 2, layout: 'canvas', page, theme, elements };
}
