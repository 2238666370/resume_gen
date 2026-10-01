/**
 * 板块显隐联动（R8-A4）。
 *
 * 画布模板决定「怎么排」，简历里的 `resume_section` 决定「显示哪些板块」。
 * 渲染与布局必须用同一套判定，否则会出现「隐藏了板块，模板却仍渲染且还占位」。
 */

interface SectionLike {
  type?: string;
  visible?: boolean;
  customId?: string;
}

const sectionsOf = (data: unknown): SectionLike[] => {
  const list = (data as { sections?: unknown } | null)?.sections;
  return Array.isArray(list) ? (list as SectionLike[]) : [];
};

/** 绑定路径 → 板块类型（与 `resume_section.section_type` 对齐）。 */
const SECTION_TYPE_BY_PATH: Record<string, string> = {
  education: 'education',
  experience: 'experience',
  internship: 'internship',
  skills: 'skills',
  projects: 'projects',
  certificates: 'certificates',
  languages: 'languages',
  customSections: 'custom',
};

export const sectionTypeOfBindPath = (path: string): string | undefined => SECTION_TYPE_BY_PATH[path];

/**
 * 过滤出应渲染的条目：
 * - 普通板块：板块整体不可见 → 返回空数组；
 * - 自定义板块：按 `customId` 逐条判定（每个自定义板块有独立显隐）。
 *
 * 缺少 `sections` 信息时一律放行，保证老数据与示例数据不受影响。
 */
export function filterVisibleItems<T>(data: unknown, path: string, items: T[]): T[] {
  const visibility = sectionTypeOfBindPath(path);
  if (!visibility) return items;

  const sections = sectionsOf(data);
  if (sections.length === 0) return items;

  if (visibility === 'custom') {
    return items.filter((item) => {
      const id = (item as { id?: string } | null)?.id;
      if (!id) return true;
      const section = sections.find((s) => s.customId === id);
      return !section || section.visible !== false;
    });
  }

  const section = sections.find((s) => s.type === visibility);
  if (!section) return items;
  return section.visible === false ? [] : items;
}
